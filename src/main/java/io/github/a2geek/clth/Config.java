package io.github.a2geek.clth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.*;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public record Config(@JsonInclude(NON_EMPTY) Map<String,Command> commands,
                     @JsonSetter(nulls=Nulls.AS_EMPTY) Map<String,TestFile> files,
                     @JsonInclude(NON_EMPTY) List<TestCase> tests) {
    public static Config load(String configDocument) throws JsonProcessingException {
        ObjectMapper mapper = new YAMLMapper();
        mapper.registerModule(new Jdk8Module());
        return mapper.readValue(configDocument, Config.class);
    }

    public record Command(@JsonProperty("main-class") @JsonInclude(NON_EMPTY) String mainClass,
                          @JsonInclude(NON_EMPTY) String executable,
                          @JsonProperty("system-exit") boolean systemExit) {}
    public record TestCase(@JsonInclude(NON_EMPTY) String name,
                           @JsonSetter(nulls=Nulls.AS_EMPTY) Map<String,Object> variables,
                           @JsonInclude(NON_EMPTY) List<Step> steps) {}
    public record Step(@JsonInclude(NON_EMPTY) String command,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stdin,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stdout,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stderr,
                       @JsonProperty("rc") int returnCode) {}
    public enum FileType { text, binary, temporary }
    public record TestFile(FileType type, String content, String prefix, String suffix) {
        public byte[] contentAsBytes() {
            return switch (type) {
                case text -> content.getBytes();
                case binary -> {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    for (String value : content.split(" ")) {
                        outputStream.write(Byte.parseByte(value, 16));
                    }
                    yield outputStream.toByteArray();
                }
                case temporary -> new byte[0];
            };
        }
        public File asFile() {
            try {
                byte[] initialData = contentAsBytes();
                String pfx = prefix;
                if (pfx == null || pfx.length() < 3) {
                    pfx = "clth-";
                }
                File file = File.createTempFile(pfx, suffix);
                if (initialData.length > 0) {
                    try (OutputStream outputStream = new FileOutputStream(file)) {
                        outputStream.write(initialData);
                    }
                }
                return file;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
