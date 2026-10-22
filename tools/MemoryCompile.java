import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.*;

class MemoryCompile {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager base = compiler.getStandardFileManager(null, null, null);
    Map<String, ByteArrayOutputStream> results = new LinkedHashMap<>();
    JavaFileManager manager = new ForwardingJavaFileManager<StandardJavaFileManager>(base) {
      public JavaFileObject getJavaFileForOutput(Location location, String name,
          JavaFileObject.Kind kind, FileObject sibling) {
        return new SimpleJavaFileObject(URI.create("mem:///" + name + kind.extension), kind) {
          public OutputStream openOutputStream() {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            results.put(name, bytes);
            return bytes;
          }
        };
      }
    };
    boolean ok = compiler.getTask(null, manager, null,
        Arrays.asList("-encoding", "UTF-8", "-source", "8", "-target", "8", "-bootclasspath", args[1],
                      "-classpath", args.length > 2 ? args[2] : "."), null,
        base.getJavaFileObjects(args[0].split(","))).call();
    if (!ok) System.exit(1);
    for (Map.Entry<String, ByteArrayOutputStream> entry : results.entrySet()) {
      System.out.println(entry.getKey() + " " + Base64.getEncoder().encodeToString(entry.getValue().toByteArray()));
    }
  }
}
