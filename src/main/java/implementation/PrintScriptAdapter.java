package implementation;

import interpreter.PrintScriptFormatter;
import interpreter.PrintScriptInterpreter;
import interpreter.PrintScriptLinter;
import interpreter.ErrorHandler;
import interpreter.InputProvider;
import interpreter.PrintEmitter;
import linter.src.main.kotlin.LintRule;
import linter.src.main.kotlin.rules.IdentifierNamingRule;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;


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

    // ==================== INTERPRETER ADAPTER ====================
    private static class PrintScriptInterpreterAdapter implements PrintScriptInterpreter {
        @Override
        public void execute(InputStream src, String version, PrintEmitter emitter,
                            ErrorHandler handler, InputProvider provider) {
            try {
                // 1. Leer el código fuente
                String sourceCode = readInputStream(src);

                // 2. Crear el lexer
                Class<?> stringCharSourceClass = Class.forName("lexer.src.main.kotlin.StringCharSource");
                Object charSource = stringCharSourceClass.getDeclaredConstructor(String.class)
                        .newInstance(sourceCode);

                Class<?> lexerClass = Class.forName("lexer.src.main.kotlin.Lexer");
                Object lexer = lexerClass.getDeclaredConstructor(Class.forName("lexer.src.main.kotlin.CharSource"))
                        .newInstance(charSource);

                // 3. Hacer split para obtener tokens
                Method splitMethod = lexerClass.getMethod("split", int.class);
                splitMethod.invoke(lexer, 8192);

                // 4. Obtener la lista de strings
                Method getListMethod = lexerClass.getMethod("getList");
                Object listField = getListMethod.invoke(lexer);

                // 5. Crear tokens
                Method createTokenMethod = lexerClass.getMethod("createToken", java.util.List.class);
                Object container = createTokenMethod.invoke(lexer, listField);

                // 6. Crear parser
                Class<?> parserClass = Class.forName("parser.src.main.kotlin.Parser");
                Object parser = parserClass.getDeclaredConstructor(
                                Class.forName("container.src.main.kotlin.Container"), String.class)
                        .newInstance(container, version);

                // 7. Parsear para obtener AST
                Method parseMethod = parserClass.getMethod("parse");
                Object ast = parseMethod.invoke(parser);

                // 8. Crear interpreter con InputProvider adaptado
                Class<?> interpreterClass = Class.forName("interpreter.src.main.kotlin.Interpreter");
                Object inputProviderAdapter = createInputProviderAdapter(provider);
                Object interpreter = interpreterClass.getDeclaredConstructor(String.class,
                                Class.forName("inputprovider.src.main.kotlin.InputProvider"))
                        .newInstance(version, inputProviderAdapter);

                // 9. Configurar PrintEmitter (esto requiere modificar el interpreter)
                configurePrintEmitter(interpreter, emitter);

                // 10. Ejecutar AST
                Method executeASTMethod = interpreterClass.getMethod("executeAST",
                        Class.forName("ast.src.main.kotlin.ASTNode"));
                executeASTMethod.invoke(interpreter, ast);

            } catch (Exception e) {
                handler.reportError("Error durante la interpretación: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private Object createInputProviderAdapter(InputProvider tckProvider) throws Exception {
            if (tckProvider == null) return null;

            // Crear un proxy que adapte el InputProvider del TCK al de tu sistema
            Class<?> inputProviderClass = Class.forName("inputprovider.src.main.kotlin.InputProvider");
            return java.lang.reflect.Proxy.newProxyInstance(
                    inputProviderClass.getClassLoader(),
                    new Class[]{inputProviderClass},
                    (proxy, method, args) -> {
                        if ("input".equals(method.getName()) && args.length == 1) {
                            return tckProvider.input((String) args[0]);
                        }
                        return null;
                    }
            );
        }

        private void configurePrintEmitter(Object interpreter, PrintEmitter emitter) {
            // Aquí necesitarías modificar tu interpreter para aceptar un PrintEmitter
            // Por ahora, redirigimos System.out temporalmente
            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(new OutputStream() {
                private StringBuilder buffer = new StringBuilder();

                @Override
                public void write(int b) throws IOException {
                    if (b == '\n') {
                        emitter.print(buffer.toString());
                        buffer.setLength(0);
                    } else {
                        buffer.append((char) b);
                    }
                }
            }));
        }
    }

    // ==================== FORMATTER ADAPTER ====================
    private static class PrintScriptFormatterAdapter implements PrintScriptFormatter {
        @Override
        public void format(InputStream src, String version, InputStream config, Writer writer) {
            try {
                /*
                // Initialize formatter
                Class<?> formatterClass = Class.forName("formatteraction.src.main.kotlin.FormatterAction");
                Object formatter = formatterClass.getDeclaredConstructor().newInstance();
                Method execute = formatterClass.getDeclaredMethod("execute", List.class);

                // Initialize arguments
                ArrayList<String> exeArgs = new ArrayList<>();
                byte[] source = src.readAllBytes();
                String texto = new String(source);
                System.out.println(texto);
                exeArgs.add(src.toString());
                exeArgs.add(config.toString());
                exeArgs.add(version);

                // Execute command
                Object output = execute.invoke(formatter, exeArgs);

                // get formatted output
                System.out.println("output: " + output);
                writer.write(String.valueOf(output));
                 */

                // 1. Leer el código fuente
                String sourceCode = readInputStream(src);

                // 2. Crear el lexer
                Class<?> stringCharSourceClass = Class.forName("lexer.src.main.kotlin.StringCharSource");
                Object charSource = stringCharSourceClass.getDeclaredConstructor(String.class).newInstance(sourceCode);

                Class<?> lexerClass = Class.forName("lexer.src.main.kotlin.Lexer");
                Object lexer = lexerClass.getDeclaredConstructor(Class.forName("lexer.src.main.kotlin.CharSource"),
                        String.class)
                    .newInstance(charSource, version);

                // 3. Hacer split para obtener tokens
                Method splitMethod = lexerClass.getMethod("split", int.class);
                splitMethod.invoke(lexer, 8192);

                // 4. Obtener la lista y crear tokens
                Method getListMethod = lexerClass.getMethod("getList");
                Object listField = getListMethod.invoke(lexer);
                //Object listField = lexerClass.getField("list").get(lexer);
                Method createTokenMethod = lexerClass.getMethod("createToken", java.util.List.class);
                Object container = createTokenMethod.invoke(lexer, listField);


                // 5. Crear archivo de configuración temporal
                File configFile = createTempConfigFile(config);


                // 6. Crear formatter y ejecutar
                Class<?> formatterClass = Class.forName("formatter.src.main.kotlin.Formatter");
                Object formatter = formatterClass.getDeclaredConstructor().newInstance();

                Method executeMethod = formatterClass.getMethod("execute",
                        Class.forName("container.src.main.kotlin.Container"), URL.class);
                Object formattedContainer = executeMethod.invoke(formatter, container, configFile.toURI().toURL());


                // 7. Convertir el container formateado de vuelta a string
                String formattedCode = containerToString(formattedContainer);
                writer.write(formattedCode);
                writer.flush();


                // Limpiar archivo temporal
                configFile.delete();
            } catch (Exception e) {
                throw new RuntimeException("Error durante el formateo: " + e.getMessage(), e);
            }
        }

        private File createTempConfigFile(InputStream config) throws IOException {
            File tempFile = File.createTempFile("format_config", ".json");
            String configuration = readInputStream(config);
            print(configuration);
            Map<String, Object> configFile = JSONToMap(configuration);
            if (configFile.isEmpty()) {
                // Configuración por defecto
                Files.write(tempFile.toPath(), getDefaultFormatConfig().getBytes());
            } else {
                String configYAML = translateRule(configFile, tempFile);
                Files.write(tempFile.toPath(), configYAML.getBytes());
            }
            return tempFile;
        }

        public static Map<String, Object> JSONToMap(String json) throws IOException {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<>() {
            });
        }

        /** Takes a list of configuration from configFile and writes it to tempFile as a translated version of the rules. */
        private String translateRule(Map<String, Object> configFile, File tempFile) throws IOException {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            String result = yamlMapper.writeValueAsString(configFile);
            System.out.println("Final config (YAML): \n" + result);
            return result;
        }

        private String getDefaultFormatConfig() {
            return "{\n" +
                    "  \"spaceBeforeColon\": false,\n" +
                    "  \"spaceAfterColon\": true,\n" +
                    "  \"spaceAroundEquals\": true,\n" +
                    "  \"lineBreakAfterSemicolon\": true\n" +
                    "}";
        }
    }

    // ==================== LINTER ADAPTER ====================
    private static class PrintScriptLinterAdapter implements PrintScriptLinter {
        @Override
                public void lint(InputStream src, String version, InputStream config, ErrorHandler handler) {
                    try {
                        System.out.println("Linter starting...");
                        // 1. Leer el código fuente
                        String sourceCode = readInputStream(src);
        
                        // 2. Crear el lexer
                        Class<?> stringCharSourceClass = Class.forName("lexer.src.main.kotlin.StringCharSource");
                        Object charSource = stringCharSourceClass.getDeclaredConstructor(String.class)
                                .newInstance(sourceCode);
        
                        Class<?> lexerClass = Class.forName("lexer.src.main.kotlin.Lexer");
                        Object lexer = lexerClass.getDeclaredConstructor(Class.forName("lexer.src.main.kotlin.CharSource"),
                                String.class)
                                .newInstance(charSource, version);
        
                        // 3. Hacer split para obtener tokens
                        Method splitMethod = lexerClass.getMethod("split", int.class);
                        splitMethod.invoke(lexer, 8192);
        
                        // 4. Obtener la lista y crear tokens
                        Method getListMethod = lexerClass.getMethod("getList");
                        Object listField = getListMethod.invoke(lexer);
                        Method createTokenMethod = lexerClass.getMethod("createToken", java.util.List.class);
                        Object container = createTokenMethod.invoke(lexer, listField);
        
                        // 5. Crear parser
                        Class<?> parserClass = Class.forName("parser.src.main.kotlin.Parser");
                        Object parser = parserClass.getDeclaredConstructor(
                                        Class.forName("container.src.main.kotlin.Container"), String.class)
                                .newInstance(container, version);
        
                        // 6. Parsear para obtener AST
                        Method parseMethod = parserClass.getMethod("parse");
                        Object ast = parseMethod.invoke(parser);
                        System.out.println("AST: " + ast);
        
                        if (ast == null) {
                            handler.reportError("Error: Failed to parse the source code.");
                            return;
                        }
        
                        // 7. Crear reglas de linting (necesitarías implementar esto basado en config)
                        List<LintRule> rules = createLintRules(config);
                        System.out.println("Rules: " + rules);
        
                        // 8. Crear linter y ejecutar
                        Class<?> linterClass = Class.forName("linter.src.main.kotlin.Linter");
                        Object linter = linterClass.getDeclaredConstructor(java.util.List.class)
                                .newInstance(rules);
        
                        Method allMethod = linterClass.getMethod("all", Class.forName("ast.src.main.kotlin.ASTNode"));
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> errors = (java.util.List<Object>) allMethod.invoke(linter, ast);
                        System.out.println("Errors: " + errors);
        
                        // 9. Reportar errores
                        for (Object error : errors) {
                            String errorMessage = error.toString(); // Necesitarías formatear mejor esto
                            handler.reportError(errorMessage);
                        }
                        System.out.println("Linter finished.");
        
                    } catch (Exception e) {
                        handler.reportError("Error durante el linting: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
    private List<LintRule> createLintRules(InputStream config) {
      List<LintRule> result = new ArrayList<>();
      try {
        String streamString = readInputStream(config);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> configMap = mapper.readValue(streamString, new TypeReference<Map<String, Object>>() {});

        // Handle "identifier_format" rule
        if (configMap.containsKey("identifier_format")) {
          String format = (String) configMap.get("identifier_format");
          String kotlinFormat = "";
          switch (format) {
            case "camel case":
              kotlinFormat = "camelCase";
              break;
            case "snake case":
              kotlinFormat = "snake_case";
              break;
            default:
              // Handle unknown format or default to a safe value
              kotlinFormat = "camelCase"; // Defaulting to camelCase
              break;
          }
          Class<?> identifierNamingRuleClass = Class.forName("linter.src.main.kotlin.rules.IdentifierNamingRule");
          result.add((LintRule) identifierNamingRuleClass.getDeclaredConstructor(String.class).newInstance(kotlinFormat));
        }

        // Handle "mandatory-variable-or-literal-in-println" rule
        if (configMap.containsKey("mandatory-variable-or-literal-in-println") && (Boolean) configMap.get("mandatory-variable-or-literal-in-println")) {
          Class<?> printLnRuleClass = Class.forName("linter.src.main.kotlin.rules.PrintLnRule");
          result.add((LintRule) printLnRuleClass.getDeclaredConstructor(boolean.class).newInstance(true));
        }

        // Handle "mandatory-variable-or-literal-in-readInput" rule
        if (configMap.containsKey("mandatory-variable-or-literal-in-readInput") && (Boolean) configMap.get("mandatory-variable-or-literal-in-readInput")) {
          Class<?> readInputRuleClass = Class.forName("linter.src.main.kotlin.rules.ReadInputRule");
          result.add((LintRule) readInputRuleClass.getDeclaredConstructor(boolean.class).newInstance(true));
        }

      } catch (Exception e) {
        System.err.println("Error creating lint rules: " + e.getMessage());
        e.printStackTrace();
      }
      return result;
    }
  }

    // ==================== UTILIDADES ====================
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

    private static String containerToString(Object container) throws Exception {
        // Initialize classes and methods
        Class<?> containerClass = container.getClass();
        Method sizeMethod = containerClass.getMethod("size");
        Method getMethod = containerClass.getMethod("get", int.class);
        Class<?> tokenClass = Class.forName("token.src.main.kotlin.Token");
        Method getContentMethod = tokenClass.getMethod("getContent");

        int size = (Integer) sizeMethod.invoke(container);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < size; i++) {
            Object token = getMethod.invoke(container, i);
            if (token != null) {
                Object content = getContentMethod.invoke(token);
                result.append(content.toString());
            }
        }
        return result.toString();
    }

    private static void print(Object obj) {
        System.out.println(obj.toString());
    }
}