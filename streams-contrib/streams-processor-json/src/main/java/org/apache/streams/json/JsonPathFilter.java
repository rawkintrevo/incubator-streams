/*
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

package org.apache.streams.json;

import org.apache.streams.core.StreamsDatum;
import org.apache.streams.core.StreamsProcessor;
import org.apache.streams.jackson.StreamsJacksonMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides a base implementation for filtering datums which
 * do not contain specific fields using JsonPath syntax.
 */
public class JsonPathFilter implements StreamsProcessor {

  private static final String STREAMS_ID = "JsonPathFilter";

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonPathFilter.class);

  private ObjectMapper mapper = StreamsJacksonMapper.getInstance();

  private String pathExpression;
  private JsonPath jsonPath;
  private String destNodeName;

  public JsonPathFilter() {
    LOGGER.info("creating JsonPathFilter");
  }

  public JsonPathFilter(String pathExpression) {
    this.pathExpression = pathExpression;
    LOGGER.info("creating JsonPathFilter for " + this.pathExpression);
  }

  @Override
  public String getId() {
    return STREAMS_ID;
  }

  @Override
  public List<StreamsDatum> process(StreamsDatum entry) {

    List<StreamsDatum> result = new ArrayList<>();

    String json = null;

    ObjectNode document = null;

    LOGGER.debug("{} processing {}", STREAMS_ID);

    if ( entry.getDocument() instanceof ObjectNode ) {
      document = (ObjectNode) entry.getDocument();
      try {
        json = mapper.writeValueAsString(document);
      } catch (JsonProcessingException ex) {
        ex.printStackTrace();
      }
    } else if ( entry.getDocument() instanceof String ) {
      json = (String) entry.getDocument();
      try {
        document = mapper.readValue(json, ObjectNode.class);
      } catch (IOException ex) {
        ex.printStackTrace();
        return null;
      }
    }

    Objects.requireNonNull(document);

    if ( StringUtils.isNotEmpty(json)) {

      Object srcResult = null;
      try {
        srcResult = jsonPath.read(json);
      } catch ( Exception ex ) {
        ex.printStackTrace();
        LOGGER.warn(ex.getMessage());
      }

      Objects.requireNonNull(srcResult);

      String[] path = StringUtils.split(pathExpression, '.');
      ObjectNode node = document;
      for (int i = 1; i < path.length - 1; i++) {
        node = (ObjectNode) document.get(path[i]);
      }

      Objects.requireNonNull(node);

      if ( srcResult instanceof JSONArray ) {
        try {
          ArrayNode jsonNode = mapper.convertValue(srcResult, ArrayNode.class);
          if ( jsonNode.size() == 1 ) {
            JsonNode item = jsonNode.get(0);
            node.set(destNodeName, item);
          } else {
            node.set(destNodeName, jsonNode);
          }
        } catch (Exception ex) {
          LOGGER.warn(ex.getMessage());
        }
      } else if ( srcResult instanceof JSONObject ) {
        try {
          ObjectNode jsonNode = mapper.convertValue(srcResult, ObjectNode.class);
          node.set(destNodeName, jsonNode);
        } catch (Exception ex) {
          LOGGER.warn(ex.getMessage());
        }
      } else if ( srcResult instanceof String ) {
        try {
          node.put(destNodeName, (String) srcResult);
        } catch (Exception ex) {
          LOGGER.warn(ex.getMessage());
        }
      }

    }

    result.add(new StreamsDatum(document));

    return result;

  }

  @Override
  public void prepare(Object configurationObject) {
    if ( configurationObject instanceof Map) {
      Map<String,String> params = ( Map<String,String>) configurationObject;
      pathExpression = params.get("pathExpression");
      jsonPath = JsonPath.compile(pathExpression);
      destNodeName = pathExpression.substring(pathExpression.lastIndexOf(".") + 1);
    }

    mapper.registerModule(new JsonOrgModule());
  }

  @Override
  public void cleanUp() {
    LOGGER.info("shutting down JsonPathFilter for " + this.pathExpression);
  }
}
