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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

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
        Method createTokenMethod = lexerClass.getMethod("createToken", List.class);
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

        System.out.println("AST real type: " + ast.getClass());
        System.out.println("Expected type: " + Class.forName("ast.src.main.kotlin.ASTNode"));
        System.out.println("Instanceof? " + Class.forName("ast.src.main.kotlin.ASTNode").isInstance(ast));

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
      return Proxy.newProxyInstance(
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

        // 4. Obtener la lista y crear tokens
        Method getListMethod = lexerClass.getMethod("getList");
        Object listField = getListMethod.invoke(lexer);
        //Object listField = lexerClass.getField("list").get(lexer);
        Method createTokenMethod = lexerClass.getMethod("createToken", List.class);
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
      if (config != null) {
        Files.copy(config, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } else {
        // Configuración por defecto
        Files.write(tempFile.toPath(), getDefaultFormatConfig().getBytes());
      }
      return tempFile;
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

        // 4. Obtener la lista y crear tokens
        Method getListMethod = lexerClass.getMethod("getList");
        Object listField = getListMethod.invoke(lexer);
        //Object listField = lexerClass.getDeclaredField("list").get(lexer);
        Method createTokenMethod = lexerClass.getMethod("createToken", List.class);
        Object container = createTokenMethod.invoke(lexer, listField);

        // 5. Crear parser
        Class<?> parserClass = Class.forName("parser.src.main.kotlin.Parser");
        Object parser = parserClass.getDeclaredConstructor(
                Class.forName("container.src.main.kotlin.Container"), String.class)
            .newInstance(container, version);

        // 6. Parsear para obtener AST
        Method parseMethod = parserClass.getMethod("parse");
        //Error null si config está vacío, ¿PORQUE?
        Object ast = parseMethod.invoke(parser);

        // 7. Crear reglas de linting (necesitarías implementar esto basado en config)
        List<LintRule> rules = createLintRules(config);

        // 8. Crear linter y ejecutar
        Class<?> linterClass = Class.forName("linter.src.main.kotlin.Linter");
        Object linter = linterClass.getDeclaredConstructor(List.class)
            .newInstance(rules);

        Method allMethod = linterClass.getMethod("all", Class.forName("ast.src.main.kotlin.ASTNode"));
        @SuppressWarnings("unchecked")
        List<Object> errors = (List<Object>) allMethod.invoke(linter, ast);

        // 9. Reportar errores
        for (Object error : errors) {
          String errorMessage = error.toString(); // Necesitarías formatear mejor esto
          handler.reportError(errorMessage);
        }

      } catch (Exception e) {
        handler.reportError("Error durante el linting: " + e.getMessage());
      }
    }

    private List<LintRule> createLintRules(InputStream config) {
      // Aquí necesitarías crear las reglas basadas en la configuración
      List<LintRule> result = new ArrayList<>();

      try {
        String streamString = readInputStream(config);
        System.out.println(streamString);
        int index = streamString.indexOf("identifier_format");
        if (index == -1){
          return new ArrayList<>();
        }
        int searchingIndex = index + 21;

        StringBuilder option = new StringBuilder();
        for (int i = searchingIndex; streamString.charAt(i) != '"'; i++){
          option.append(streamString.charAt(i));
        }

        switch (option.toString()){
          case "camel case":
            option = new StringBuilder("camelCase");
            break;
          case "snake case":
            option = new StringBuilder("snake_case");
            break;
          default:
        }

        System.out.println(option);

        result.add(new IdentifierNamingRule(option.toString()));

      } catch (IOException e) {
        throw new RuntimeException(e);
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
    // Necesitarías implementar esto basado en tu clase Container
    // Por ahora, una implementación básica
    Class<?> containerClass = container.getClass();
    Method sizeMethod = containerClass.getMethod("size");
    Method getMethod = containerClass.getMethod("get", int.class);

    int size = (Integer) sizeMethod.invoke(container);
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < size; i++) {
      Object token = getMethod.invoke(container, i);
      if (token != null) {
        // Obtener el contenido del token
        Method getContentMethod = token.getClass().getMethod("getContent");
        Object content = getContentMethod.invoke(token);
        //Object content = token.getClass().getField("content").get(token);
        result.append(content.toString());
        if (i < size - 1) {
          result.append(" ");
        }
      }
    }

    return result.toString();
  }

}