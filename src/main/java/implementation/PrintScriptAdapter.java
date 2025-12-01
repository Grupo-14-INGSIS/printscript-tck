package implementation;

import ast.src.main.kotlin.ASTNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import container.src.main.kotlin.Container;
import formatter.src.main.kotlin.Formatter;
import interpreter.PrintScriptFormatter;
import interpreter.PrintScriptInterpreter;
import interpreter.PrintScriptLinter;
import interpreter.ErrorHandler;
import interpreter.InputProvider;
import interpreter.PrintEmitter;
import interpreter.src.main.kotlin.Interpreter;
import lexer.src.main.kotlin.Lexer;
import lexer.src.main.kotlin.StringCharSource;
import linter.src.main.kotlin.Linter;
import linter.src.main.kotlin.LintError;
import linter.src.main.kotlin.LintRule;
import linter.src.main.kotlin.config.ConfigFactory;
import linter.src.main.kotlin.config.ConfigLoader;
import linter.src.main.kotlin.config.LinterConfig;
import linter.src.main.kotlin.rules.IdentifierNamingRule;
import linter.src.main.kotlin.rules.PrintLnRule;
import linter.src.main.kotlin.rules.ReadInputRule;
import parser.src.main.kotlin.Parser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class PrintScriptAdapter implements PrintScriptFactory {

    @Override
    public PrintScriptInterpreter interpreter() {
        return new PrintScriptInterpreterAdapter();
    }

    @Override
    public PrintScriptFormatter formatter() {
        return new PrintScriptFormatterAdapter();
    }

    @Override
    public PrintScriptLinter linter() {
        return new PrintScriptLinterAdapter();
    }

    private static class PrintScriptInterpreterAdapter implements PrintScriptInterpreter {
        @Override
        public void execute(InputStream src, String version, PrintEmitter emitter, ErrorHandler handler, InputProvider provider) {
            try {
                String sourceCode = readInputStream(src);
                Lexer lexer = new Lexer(new StringCharSource(sourceCode), version);
                List<Container> statements = lexer.lexIntoStatements();

                inputprovider.src.main.kotlin.InputProvider inputProviderAdapter = createInputProviderAdapter(provider, emitter);
                Function1<Object, Unit> printer = createPrinterAdapter(emitter);

                Interpreter interpreter = new Interpreter(version, inputProviderAdapter, printer);

                for (Container statement : statements) {
                    Parser parser = new Parser(statement, version);
                    ASTNode ast = parser.parse();
                    interpreter.interpret(ast);
                }
            } catch (Exception e) {
                handler.reportError("Error during interpretation: " + e.getMessage());
            }
        }

        private inputprovider.src.main.kotlin.InputProvider createInputProviderAdapter(InputProvider tckProvider, PrintEmitter emitter) {
            if (tckProvider == null) return null;
            return new inputprovider.src.main.kotlin.InputProvider() {
                @Override
                public String readInput(String message) {
                    emitter.print(message);
                    return tckProvider.input(message);
                }

                @Override
                public String readEnv(String name) {
                    return System.getenv(name);
                }
            };
        }

        private Function1<Object, Unit> createPrinterAdapter(PrintEmitter emitter) {
            return (message) -> {
                if (message != null) {
                    emitter.print(message.toString());
                } else {
                    emitter.print("null");
                }
                return Unit.INSTANCE;
            };
        }
    }

    private static class PrintScriptFormatterAdapter implements PrintScriptFormatter {
        @Override
        public void format(InputStream src, String version, InputStream config, Writer writer) {
            try {
                String sourceCode = readInputStream(src);
                Lexer lexer = new Lexer(new StringCharSource(sourceCode), version);
                List<Container> statements = lexer.lexIntoStatements();
                File configFile = createTempConfigFile(config);
                Formatter formatter = new Formatter();
                List<Container> formattedStatements = formatter.execute(statements, configFile);
                String formattedCode = statementsToString(formattedStatements);
                writer.write(formattedCode);
                writer.flush();
                configFile.delete();
            }
            catch (Exception e) {
                throw new RuntimeException("Error during formatting: " + e.getMessage(), e);
            }
        }
        
        private File createTempConfigFile(InputStream config) throws IOException {
            File tempFile = File.createTempFile("temp_config_formatter", ".yml");
            String jsonConfig = readInputStream(config);
            ObjectMapper jsonMapper = new ObjectMapper();
            Map<String, Object> jsonMap = jsonMapper.readValue(jsonConfig, new TypeReference<>() {});
            
            // The formatter's ConfigLoader expects a flat map, not nested under "rules"
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            String yamlConfig = yamlMapper.writeValueAsString(jsonMap);
            try (FileWriter fileWriter = new FileWriter(tempFile)) {
                fileWriter.write(yamlConfig);
            }
            return tempFile;
        }
    }

    private static class PrintScriptLinterAdapter implements PrintScriptLinter {
        @Override
        public void lint(InputStream src, String version, InputStream config, ErrorHandler handler) {
            File configFile = null;
            try {
                String sourceCode = readInputStream(src);
                Lexer lexer = new Lexer(new StringCharSource(sourceCode), version);
                List<Container> statements = lexer.lexIntoStatements();

                List<ASTNode> asts = new ArrayList<>();
                for (Container statement : statements) {
                    Parser parser = new Parser(statement, version);
                    asts.add(parser.parse());
                }

                configFile = createTempConfigFile(config);
                List<LintRule> rules = loadLintRules(configFile.getAbsolutePath());
                Linter linter = new Linter(rules);
                List<LintError> errors = linter.lint(asts);

                for (LintError error : errors) {
                    handler.reportError(error.toString());
                }
            }
            catch (Exception e) {
                handler.reportError("Error during linting: " + e.getMessage());
            } finally {
                if (configFile != null) {
                    configFile.delete();
                }
            }
        }

        private File createTempConfigFile(InputStream config) throws IOException {
            File tempFile = File.createTempFile("temp_config_linter", ".yml");
            String jsonConfig = readInputStream(config);
            
            Map<String, Object> rootMap;

            if (jsonConfig.isEmpty() || jsonConfig.equals("{}")) {
                rootMap = Map.of("rules", new HashMap<>());
            } else {
                ObjectMapper jsonMapper = new ObjectMapper();
                Map<String, Object> jsonMap = jsonMapper.readValue(jsonConfig, new TypeReference<>() {});

                Map<String, Object> rulesMap = new HashMap<>();
                
                if (jsonMap.containsKey("identifier_format")) {
                    String style = (String) jsonMap.get("identifier_format");
                    if ("snake case".equals(style)) {
                        style = "snake_case";
                    } else if ("camel case".equals(style)) {
                        style = "camelCase";
                    }
                    rulesMap.put("identifier_format", Map.of("style", style));
                }
                
                if (jsonMap.containsKey("mandatory-variable-or-literal-in-println")) {
                    rulesMap.put("mandatory-variable-or-literal-in-println", Map.of("enabled", jsonMap.get("mandatory-variable-or-literal-in-println")));
                }
                
                if (jsonMap.containsKey("mandatory-variable-or-literal-in-readInput")) {
                    rulesMap.put("mandatory-variable-or-literal-in-readInput", Map.of("enabled", jsonMap.get("mandatory-variable-or-literal-in-readInput")));
                }
                
                rootMap = Map.of("rules", rulesMap);
            }

            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            String yamlConfig = yamlMapper.writeValueAsString(rootMap);
            
            try (FileWriter fileWriter = new FileWriter(tempFile)) {
                fileWriter.write(yamlConfig);
            }
            return tempFile;
        }

        private List<LintRule> loadLintRules(String configPath) {
            ConfigLoader loader = new ConfigLoader();
            Map<String, Object> yamlMap = loader.loadYaml(configPath);
            if (yamlMap == null || yamlMap.isEmpty()) {
                return new ArrayList<>();
            }
            
            ConfigFactory factory = new ConfigFactory();
            LinterConfig config = factory.createConfig(yamlMap);

            List<LintRule> rules = new ArrayList<>();
            if (config.getRules().getIdentifier_format() != null) {
                rules.add(new IdentifierNamingRule(config.getRules().getIdentifier_format().getStyle()));
            }
            if (config.getRules().getMandatory_variable_or_literal_in_println() != null) {
                rules.add(new PrintLnRule(config.getRules().getMandatory_variable_or_literal_in_println().getEnabled()));
            }
            if (config.getRules().getMandatory_variable_or_literal_in_readInput() != null) {
                 rules.add(new ReadInputRule(config.getRules().getMandatory_variable_or_literal_in_readInput().getEnabled()));
            }
            return rules;
        }
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    private static String statementsToString(List<Container> statements) {
        StringBuilder result = new StringBuilder();
        for (Container statement : statements) {
            for (token.src.main.kotlin.Token token : statement.getContainer()) {
                if (token != null) {
                    result.append(token.getContent());
                }
            }
        }
        return result.toString();
    }
}
