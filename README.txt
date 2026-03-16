Multiplayer Trivia Game - Distributed Systems Assignment 1

JDK used:
- JDK 17 or newer (tested with JDK 21)

How to compile:
1) Open terminal in the project root
2) Run:
   javac -d out src/common/*.java src/model/*.java src/server/*.java src/client/*.java

How to run the server:
   java -cp out server.TriviaServer

How to run a client:
   java -cp out client.TriviaClient

Sample users:
- ahmed / 1234
- sara / 1234
- ali / 1234
- mona / 1234

Main commands after login:
- MENU
- PLAY_SINGLE <category> <difficulty> <questionCount>
- CREATE_TEAM <teamName>
- JOIN_TEAM <teamName>
- LIST_TEAMS
- START_TEAM_GAME <yourTeamName> <otherTeamName> <category> <difficulty> <questionCount>
- HISTORY
- ANSWER A
- -   (quit)

Example single-player flow:
LOGIN ahmed 1234
PLAY_SINGLE Science easy 2
ANSWER B
ANSWER B

Example team flow:
Client 1:
LOGIN ahmed 1234
CREATE_TEAM Alpha

Client 2:
LOGIN sara 1234
JOIN_TEAM Alpha

Client 3:
LOGIN ali 1234
CREATE_TEAM Beta

Client 4:
LOGIN mona 1234
JOIN_TEAM Beta

Client 1 starts the match:
START_TEAM_GAME Alpha Beta Math easy 2

Notes / assumptions:
- Answers are case-insensitive.
- Only the first answer per player per question is accepted.
- Late answers are ignored.
- If a player disconnects, the server removes the player from the team/session.
- Team names must be unique.
