import implementation.CustomImplementationFactory;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import interpreter.PrintEmitter;
import interpreter.ErrorHandler;
import interpreter.InputProvider;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SimpleAdapterTest {

  private CustomImplementationFactory factory;
  private List<String> outputs;
  private List<String> errors;

  @Before
  public void setUp() {
    factory = new CustomImplementationFactory();
    outputs = new ArrayList<>();
    errors = new ArrayList<>();
  }

  @Test
  public void testFactoryCreation() {
    assertNotNull("Factory should not be null", factory);
    assertNotNull("Interpreter should not be null", factory.interpreter());
    assertNotNull("Formatter should not be null", factory.formatter());
    assertNotNull("Linter should not be null", factory.linter());
  }

  @Test
  public void testInterpreterBasic() {
    try {
      String code = "let x: number = 42;";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));

      PrintEmitter emitter = message -> outputs.add(message);
      ErrorHandler handler = message -> errors.add(message);
      InputProvider provider = name -> "test";

      var interpreter = factory.interpreter();
      interpreter.execute(input, "1.0", emitter, handler, provider);

      // El test pasa si no lanza excepción
      assertTrue("Test should complete without throwing exception", true);

    } catch (Exception e) {
      fail("Interpreter test failed: " + e.getMessage());
    }
  }

  @Test
  public void testFormatterBasic() {
    try {
      String code = "let x:number=42;";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
      ByteArrayInputStream config = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
      StringWriter writer = new StringWriter();

      var formatter = factory.formatter();
      formatter.format(input, "1.0", config, writer);

      String result = writer.toString();
      assertNotNull("Formatted result should not be null", result);

    } catch (Exception e) {
      fail("Formatter test failed: " + e.getMessage());
    }
  }

  @Test
  public void testLinterBasic() {
    try {
      String code = "let x: number = 42;";
      ByteArrayInputStream input = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
      ByteArrayInputStream config = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));

      ErrorHandler handler = message -> errors.add(message);

      var linter = factory.linter();
      linter.lint(input, "1.0", config, handler);

      // El test pasa si no lanza excepción
      assertTrue("Test should complete without throwing exception", true);

    } catch (Exception e) {
      fail("Linter test failed: " + e.getMessage());
    }
  }

  @Test
  public void testClassAvailability() {
    try {
      // Verificar que las clases principales están disponibles
      Class.forName("lexer.src.main.kotlin.Lexer");
      Class.forName("parser.src.main.kotlin.Parser");
      Class.forName("interpreter.src.main.kotlin.Interpreter");
      Class.forName("formatter.src.main.kotlin.Formatter");
      Class.forName("linter.src.main.kotlin.Linter");

      assertTrue("All main classes should be available", true);

    } catch (ClassNotFoundException e) {
      fail("Required class not found: " + e.getMessage());
    }
  }
}