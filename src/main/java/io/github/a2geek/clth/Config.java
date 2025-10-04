package io.github.a2geek.clth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public record Config(@JsonInclude(NON_EMPTY) Map<String,Command> commands,
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
                           Map<String,Object> variables,
                           @JsonInclude(NON_EMPTY) List<Step> steps) {}
    public record Step(@JsonInclude(NON_EMPTY) String command,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stdin,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stdout,
                       @JsonSetter(nulls=Nulls.AS_EMPTY)String stderr,
                       @JsonProperty("rc") int returnCode) {}

}
