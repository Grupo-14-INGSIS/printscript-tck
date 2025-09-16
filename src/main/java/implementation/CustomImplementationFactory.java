package implementation;

import interpreter.PrintScriptFormatter;
import interpreter.PrintScriptInterpreter;
import interpreter.PrintScriptLinter;

public class CustomImplementationFactory implements PrintScriptFactory {

  private final PrintScriptAdapter adapter = new PrintScriptAdapter();

  @Override
  public PrintScriptInterpreter interpreter() {
    return adapter.interpreter();
  }

  @Override
  public PrintScriptFormatter formatter() {
    return adapter.formatter();
  }

  @Override
  public PrintScriptLinter linter() {
    return adapter.linter();
  }
}