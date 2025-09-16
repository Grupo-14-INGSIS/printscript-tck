package implementation;

// Archivo temporal para explorar qué clases están disponibles
import java.lang.reflect.*;

public class ClassExplorer {
  public static void main(String[] args) {
    String[] possibleClasses = {
        // Módulos principales disponibles
        "interpreter.src.main.kotlin.Interpreter",
        "cli.src.main.kotlin.Cli",
        "lexer.src.main.kotlin.Lexer",
        "linter.src.main.kotlin.Linter",
        "parser.src.main.kotlin.Parser",
        "formatter.src.main.kotlin.Formatter",
        "executor.src.main.kotlin.Executor",

        // Clases de datos
        "ast.src.main.kotlin.ASTNode",
        "token.src.main.kotlin.Token",
        "tokendata.src.main.kotlin.TokenData",
        "container.src.main.kotlin.Container",
        "inputprovider.src.main.kotlin.InputProvider",
        "analyzer.src.main.kotlin.Analyzer",
        "formatteraction.src.main.kotlin.FormatterAction",
        "progress.src.main.kotlin.Progress",

    };


    System.out.println("Explorando clases de IngsisTP (versión 1.1.202509160331296)...\n");

    for (String className : possibleClasses) {
      try {
        Class<?> clazz = Class.forName(className);
        System.out.println("✓ ENCONTRADA: " + className);
        System.out.println("  Tipo: " + (clazz.isInterface() ? "Interface" : "Class"));

        // Mostrar constructores públicos
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        System.out.println("  Constructores públicos:");
        boolean hasPublicConstructor = false;
        for (Constructor<?> constructor : constructors) {
          if (Modifier.isPublic(constructor.getModifiers())) {
            System.out.println("    - " + constructor.toString().replaceAll(".*\\.", ""));
            hasPublicConstructor = true;
          }
        }
        if (!hasPublicConstructor) {
          System.out.println("    - No hay constructores públicos (puede ser object Kotlin)");
        }

        // Mostrar algunos métodos públicos importantes
        Method[] methods = clazz.getDeclaredMethods();
        System.out.println("  Métodos públicos relevantes:");
        int count = 0;
        for (Method method : methods) {
          if (Modifier.isPublic(method.getModifiers()) && count < 8) {
            String params = "";
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length > 0) {
              params = java.util.Arrays.stream(paramTypes)
                  .map(Class::getSimpleName)
                  .reduce((a, b) -> a + ", " + b)
                  .orElse("");
            }
            System.out.println("    - " + method.getName() + "(" + params + ") : " + method.getReturnType().getSimpleName());
            count++;
          }
        }

        // Verificar si es un object Kotlin (singleton)
        try {
          Field instanceField = clazz.getField("INSTANCE");
          if (Modifier.isStatic(instanceField.getModifiers())) {
            System.out.println("  ⭐ Es un Kotlin object - usar: " + className + ".INSTANCE");
          }
        } catch (NoSuchFieldException ignored) {}

        System.out.println();

      } catch (ClassNotFoundException e) {
        System.out.println("✗ No encontrada: " + className);
      } catch (Exception e) {
        System.out.println("? Error con: " + className + " - " + e.getMessage());
      }
    }

    System.out.println("\n=== Exploración completada ===");
    System.out.println("Usa las clases marcadas con ✓ en tu implementación");
    System.out.println("Las marcadas con ⭐ son Kotlin objects - usar .INSTANCE");
  }
}