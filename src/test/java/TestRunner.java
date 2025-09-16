
import implementation.CustomImplementationFactory;
import interpreter.PrintEmitter;
import interpreter.ErrorHandler;
import interpreter.InputProvider;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Clase para probar manualmente el adapter antes de ejecutar el TCK completo
 */
public class TestRunner {

  public static void main(String[] args) {
    System.out.println("=== Testing PrintScript Adapter ===\n");

    CustomImplementationFactory factory = new CustomImplementationFactory();

    // Test 1: Interpreter
    testInterpreter(factory);

    // Test 2: Formatter
    testFormatter(factory);

    // Test 3: Linter
    testLinter(factory);

    System.out.println("=== Tests completed ===");
  }

  private static void testInterpreter(CustomImplementationFactory factory) {
    System.out.println("--- Testing Interpreter ---");
    try {
      String code = "let x: number = 5;\nprintln(x);";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));

      StringBuilder output = new StringBuilder();
      StringBuilder errors = new StringBuilder();

      PrintEmitter emitter = message -> {
        output.append(message).append("\n");
        System.out.println("Output: " + message);
      };

      ErrorHandler handler = message -> {
        errors.append(message).append("\n");
        System.out.println("Error: " + message);
      };

      InputProvider provider = name -> {
        System.out.println("Input requested for: " + name);
        return "test_input";
      };

      var interpreter = factory.interpreter();
      interpreter.execute(input, "1.0", emitter, handler, provider);

      System.out.println("Interpreter test completed");
      if (errors.length() > 0) {
        System.out.println("Errors found: " + errors.toString());
      }

    } catch (Exception e) {
      System.out.println("Interpreter test failed: " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println();
  }

  private static void testFormatter(CustomImplementationFactory factory) {
    System.out.println("--- Testing Formatter ---");
    try {
      String code = "let x:number=5;println(x);";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
      ByteArrayInputStream config = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
      StringWriter output = new StringWriter();

      var formatter = factory.formatter();
      formatter.format(input, "1.0", config, output);

      System.out.println("Original: " + code);
      System.out.println("Formatted: " + output.toString());
      System.out.println("Formatter test completed");

    } catch (Exception e) {
      System.out.println("Formatter test failed: " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println();
  }

  private static void testLinter(CustomImplementationFactory factory) {
    System.out.println("--- Testing Linter ---");
    try {
      String code = "let x: number = 5;\nprintln(x);";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
      ByteArrayInputStream config = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));

      StringBuilder errors = new StringBuilder();
      ErrorHandler handler = message -> {
        errors.append(message).append("\n");
        System.out.println("Lint Error: " + message);
      };

      var linter = factory.linter();
      linter.lint(input, "1.0", config, handler);

      if (errors.length() == 0) {
        System.out.println("No lint errors found");
      } else {
        System.out.println("Lint errors: " + errors.toString());
      }
      System.out.println("Linter test completed");

    } catch (Exception e) {
      System.out.println("Linter test failed: " + e.getMessage());
      e.printStackTrace();
    }
    System.out.println();
  }
}
