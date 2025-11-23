/*
 * Command Line Test Harness
 * Copyright (C) 2025  Robert Greene
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.a2geek.clth;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.*;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    public record TestFile(FileType type, String content, String prefix, String suffix) {
        public byte[] contentAsBytes() {
            return switch (type) {
                case text -> content.getBytes();
                case binary -> {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    for (String value : content.split("\\s+")) {
                        outputStream.write((byte)HexFormat.fromHexDigits(value));
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
    public record TestCase(@JsonInclude(NON_EMPTY) String name,
                           @JsonSetter(nulls=Nulls.AS_EMPTY) Map<String,Object> variables,
                           @JsonInclude(NON_EMPTY) List<Step> steps) {}
    public record Step(@JsonInclude(NON_EMPTY) @JsonDeserialize(converter = ArrayConverter.class) List<String> command,
                       @JsonSetter(nulls=Nulls.AS_EMPTY) String stdin,
                       @JsonSetter(nulls=Nulls.AS_EMPTY) String stdout,
                       @JsonSetter(nulls=Nulls.AS_EMPTY) String stderr,
                       MatchCriteria criteria,
                       @JsonProperty("rc") int returnCode) {
        @Override
        public MatchCriteria criteria() {
            return criteria == null ? new MatchCriteria(MatchType.exact, Whitespace.exact) : criteria;
        }
    }
    public record MatchCriteria(MatchType match, Whitespace whitespace) {
        @Override
        public MatchType match() {
            return match == null ? MatchType.exact : match;
        }
        @Override
        public Whitespace whitespace() {
            return whitespace == null ? Whitespace.exact : whitespace;
        }
    }

    public enum FileType { text, binary, temporary }

    public enum MatchType {
        exact(String::equals),
        ignore((expected,actual) -> true),
        contains((expected, actual) -> actual.contains(expected)),
        regex((regex,actual) -> Pattern.compile(regex, Pattern.DOTALL).matcher(actual).matches());

        private final BiFunction<String,String,Boolean> matchFn;

        MatchType(BiFunction<String,String,Boolean> matchFn) {
            this.matchFn = matchFn;
        }

        public boolean matches(String expected, String actual) {
            return matchFn.apply(expected, actual);
        }
    }

    public enum Whitespace {
        exact(s -> s),
        trim(Whitespace::multilineTrim),
        ignore(Whitespace::whitespaceTrim);

        private final Function<String,String> whitespaceFn;

        Whitespace(Function<String,String> whitespaceFn) {
            this.whitespaceFn = whitespaceFn;
        }
        public String apply(String value) {
            return whitespaceFn.apply(value);
        }
        public static String multilineTrim(String value) {
            return value.lines().map(String::trim).collect(Collectors.joining("\n"));
        }
        public static String whitespaceTrim(String value) {
            return value.lines()
                    .map(s -> s.replaceAll("\\s+", " "))
                    .map(String::trim)
                    .collect(Collectors.joining("\n"));
        }
    }

    public static class ArrayConverter implements Converter<Object,List<String>> {
        @Override
        public List<String> convert(Object value) {
            if (value instanceof String str) {
                return List.of(str.split(" "));
            }
            else if (value instanceof List<?> list) {
                List<String> args = new ArrayList<>();
                list.forEach(o -> args.add(o.toString()));
                return args;
            }
            throw new RuntimeException("Unexpected source type: " + value.getClass().getName());
        }
        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(Object.class);
        }
        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructCollectionType(List.class, String.class);
        }
    }
}
