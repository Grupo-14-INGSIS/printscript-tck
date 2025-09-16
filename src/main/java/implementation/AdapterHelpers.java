package implementation;

import interpreter.InputProvider;
import interpreter.PrintEmitter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Clases de soporte para el adapter de PrintScript
 */
public class AdapterHelpers {

  /**
   * Captura la salida de System.out y la redirige a un PrintEmitter
   */
  public static class OutputCapture {
    private final PrintStream originalOut;
    private final PrintEmitter emitter;
    private final ByteArrayOutputStream buffer;
    private final PrintStream captureStream;

    public OutputCapture(PrintEmitter emitter) {
      this.originalOut = System.out;
      this.emitter = emitter;
      this.buffer = new ByteArrayOutputStream();
      this.captureStream = new PrintStream(buffer) {
        @Override
        public void println(String s) {
          emitter.print(s);
          super.println(s);
        }

        @Override
        public void print(String s) {
          if (s.contains("\n")) {
            String[] lines = s.split("\n");
            for (int i = 0; i < lines.length; i++) {
              if (i > 0 || !lines[i].isEmpty()) {
                emitter.print(lines[i]);
              }
            }
          } else {
            emitter.print(s);
          }
          super.print(s);
        }
      };
    }

    public void start() {
      System.setOut(captureStream);
    }

    public void stop() {
      System.setOut(originalOut);
    }

    public String getCapturedOutput() {
      return buffer.toString();
    }
  }

  /**
   * Adapter para InputProvider entre TCK y tu sistema
   */
  public static class InputProviderAdapter {
    private final InputProvider tckProvider;

    public InputProviderAdapter(InputProvider tckProvider) {
      this.tckProvider = tckProvider;
    }

    public String input(String name) {
      return tckProvider != null ? tckProvider.input(name) : "";
    }
  }

  /**
   * Manejo seguro de reflection
   */
  public static class ReflectionUtils {

    public static Object createInstance(String className) throws Exception {
      Class<?> clazz = Class.forName(className);
      return createInstance(clazz);
    }

    public static Object createInstance(Class<?> clazz) throws Exception {
      try {
        // Intentar obtener INSTANCE (para Kotlin objects)
        java.lang.reflect.Field instanceField = clazz.getField("INSTANCE");
        if (java.lang.reflect.Modifier.isStatic(instanceField.getModifiers())) {
          return instanceField.get(null);
        }
      } catch (NoSuchFieldException ignored) {}

      // Si no es un Kotlin object, usar constructor normal
      try {
        return clazz.getDeclaredConstructor().newInstance();
      } catch (NoSuchMethodException e) {
        // Buscar constructor con parámetros mínimos
        var constructors = clazz.getDeclaredConstructors();
        if (constructors.length > 0) {
          var constructor = constructors[0];
          constructor.setAccessible(true);

          // Crear parámetros por defecto basados en tipo
          Object[] params = new Object[constructor.getParameterCount()];
          Class<?>[] paramTypes = constructor.getParameterTypes();

          for (int i = 0; i < params.length; i++) {
            params[i] = getDefaultValue(paramTypes[i]);
          }

          return constructor.newInstance(params);
        }
      }

      throw new RuntimeException("No se pudo crear instancia de " + clazz.getName());
    }

    private static Object getDefaultValue(Class<?> type) {
      if (type == String.class) return "";
      if (type == int.class || type == Integer.class) return 0;
      if (type == boolean.class || type == Boolean.class) return false;
      if (type == double.class || type == Double.class) return 0.0;
      if (type == float.class || type == Float.class) return 0.0f;
      if (type == long.class || type == Long.class) return 0L;
      if (type.isAssignableFrom(java.util.List.class)) return new java.util.ArrayList<>();
      return null;
    }

    public static Object invokeMethod(Object instance, String methodName, Object... args) throws Exception {
      Class<?> clazz = instance.getClass();
      Class<?>[] paramTypes = new Class[args.length];

      for (int i = 0; i < args.length; i++) {
        paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
      }

      try {
        var method = clazz.getMethod(methodName, paramTypes);
        return method.invoke(instance, args);
      } catch (NoSuchMethodException e) {
        // Buscar método con nombre similar
        for (var method : clazz.getMethods()) {
          if (method.getName().equals(methodName) &&
              method.getParameterCount() == args.length) {
            return method.invoke(instance, args);
          }
        }
        throw e;
      }
    }
  }

  /**
   * Configuración por defecto para diferentes componentes
   */
  public static class DefaultConfigs {

    public static String getFormatterConfig() {
      return """
                {
                    "spaceBeforeColon": false,
                    "spaceAfterColon": true,
                    "spaceAroundEquals": true,
                    "lineBreakAfterSemicolon": true,
                    "indentSize": 2,
                    "printLineBreaks": 1
                }
                """;
    }

    public static String getLinterConfig() {
      return """
                {
                    "identifier_format": "camelCase",
                    "mandatory_variable_or_literal_in_println": true,
                    "mandatory_variable_or_literal_in_readInput": true
                }
                """;
    }
  }

  /**
   * Manejo de errores específicos del adapter
   */
  public static class AdapterException extends RuntimeException {
    private final String component;

    public AdapterException(String component, String message, Throwable cause) {
      super(String.format("[%s] %s", component, message), cause);
      this.component = component;
    }

    public AdapterException(String component, String message) {
      this(component, message, null);
    }

    public String getComponent() {
      return component;
    }
  }
}