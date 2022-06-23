// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example.graphwebhook;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GraphwebhookApplication {

    /**
     * @return A configured SocketIO server instance
     */
    @Bean
    public SocketIOServer socketIOServer() {
        var config = new Configuration();
        config.setHostname("localhost");
        config.setPort(8081);
        return new SocketIOServer(config);
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) throws JsonProcessingException {
//        SpringApplication.run(GraphwebhookApplication.class, args);
//      System.out.println("hello world");

      String decryptedData = """
          {
            "@odata.context": "https://graph.microsoft.com/$metadata#teams('59211b1f-958d-4bb0-bafc-435baccdb36f')/channels('19%3A43202f23325c4970822bcc67b11ec32b%40thread.tacv2')/messages('1655943433040')/replies/$entity",
            "id": "1655943443371",
            "replyToId": "1655943433040",
            "etag": "1655943443371",
            "messageType": "unknownFutureValue",
            "createdDateTime": "2022-06-23T00:17:23.371Z",
            "lastModifiedDateTime": "2022-06-23T00:17:23.371Z",
            "lastEditedDateTime": null,
            "deletedDateTime": null,
            "subject": null,
            "summary": null,
            "chatId": null,
            "importance": "normal",
            "locale": "en-us",
            "webUrl": "https://teams.microsoft.com/l/message/19%3A43202f23325c4970822bcc67b11ec32b%40thread.tacv2/1655943443371?groupId=59211b1f-958d-4bb0-bafc-435baccdb36f&tenantId=b0c9e4f9-d72d-406f-b247-e8d86c4b416a&createdTime=1655943443371&parentMessageId=1655943433040",
            "from": null,
            "body": {
              "contentType": "html",
              "content": "<systemEventMessage/>"
            },
            "channelIdentity": {
              "teamId": "59211b1f-958d-4bb0-bafc-435baccdb36f",
              "channelId": "19:43202f23325c4970822bcc67b11ec32b@thread.tacv2"
            },
            "attachments": [],
            "mentions": [],
            "onBehalfOf": null,
            "policyViolation": null,
            "reactions": [],
            "replies": [],
            "hostedContents": [],
            "eventDetail": {
              "@odata.type": "#Microsoft.Teams.GraphSvc.callEndedEventMessageDetail",
              "callId": null,
              "callDuration": "PT16S",
              "callEventType": "meeting",
              "callParticipants": [
                {
                  "participant": {
                    "application": null,
                    "device": null,
                    "user": {
                      "userIdentityType": "aadUser",
                      "id": "782faabb-7c4e-4bf4-b4bb-041d17e3f179",
                      "displayName": "FNU LNU"
                    }
                  }
                }
              ],
              "initiator": {
                "application": null,
                "device": null,
                "user": {
                  "userIdentityType": "aadUser",
                  "id": "782faabb-7c4e-4bf4-b4bb-041d17e3f179",
                  "displayName": null
                }
              }
            }
          }
          """;
    }
}
