package com.anvil.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppServerIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createThreadAndStartScriptedRun() throws Exception {
        String threadJson = mockMvc.perform(post("/v1/threads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cwd\":\"fixtures/repos/sample-lib\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thread_id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String threadId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(threadJson)
                .get("thread_id")
                .asText();

        mockMvc.perform(post("/v1/threads/" + threadId + "/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"agent","model":"scripted:read-add","message":"read Add.java"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.run_id").exists());
    }
}
