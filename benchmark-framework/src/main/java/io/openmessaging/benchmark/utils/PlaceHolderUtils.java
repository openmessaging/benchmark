/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public class PlaceHolderUtils {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    private static final Logger log = LoggerFactory.getLogger(PlaceHolderUtils.class);

    public static <T> T readAndApplyPlaceholders(File file, Class<T> clazz) throws IOException {
        String content = readAndApplyPlaceholders(file);
        return mapper.readValue(content, clazz);
    }

    public static String readAndApplyPlaceholders(File file) throws IOException  {
        try (FileInputStream in = new FileInputStream(file)) {
            String content = IOUtils.toString(in, StandardCharsets.UTF_8);
            return readAndApplyPlaceholders(content);
        }
    }

    public static String readAndApplyPlaceholders(String content) {
        Map<String, String> variables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            variables.put(entry.getKey(), entry.getValue());
        };
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            variables.put(entry.getKey() + "", entry.getValue() + "");
        }
        log.debug("readAndApplyPlaceholders before {}", content);
        StrSubstitutor sub = new StrSubstitutor(variables);
        String result =  sub.replace(content);
        log.debug("readAndApplyPlaceholders result {}", result);
        return result;
    }
}
