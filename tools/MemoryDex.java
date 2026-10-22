import com.android.tools.r8.*;
import java.nio.file.*;
import java.util.*;
class MemoryDex {
  public static void main(String[] args) throws Exception {
    D8Command command = D8Command.builder()
      .addProgramFiles(Arrays.stream(args[0].split(",")).map(Paths::get).toArray(Path[]::new))
      .addLibraryFiles(Paths.get(args[1]))
      .setMinApiLevel(36)
      .setProgramConsumer(new DexIndexedConsumer() {
        public void accept(int index, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
          System.out.println(Base64.getEncoder().encodeToString(data.copyByteData()));
        }
        public void finished(DiagnosticsHandler handler) {}
      }).build();
    D8.run(command);
  }
}
