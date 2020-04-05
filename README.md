# Using Bots with a BPMN
(This Project was migrated from https://github.com/pme123/play-scala-camunda-bot)

The following main points are done:
* Interact with a Business Process throw a Message Bot.
  * Camunda BPMN
  * Canoe Telegram Client
* Use a pure functional implementation - it uses ZIO as Effect System.

# Setup
## Running the Server
    
    mill server.run
    
## Camunda

Make sure that Camunda is running on port 8080. I use there Docker Image.

Deploy the Process `example-process.bpmn`: http://localhost:8080/camunda/app/cockpit/default/#/repository

## Telegram

Setup the Bot like described here: https://github.com/pme123/play-scala-telegrambot4s#setup-the-bot 

Create a Group in your Telegram Client. Add as many colleagues as you like. 

Add the commands to your newly created bot:
```
register - Registers you to the Bot.
mytasks - My pending Tasks.
newtask - Report an Issue.
``` 
Open the chat with your Bot and type `\register`.

This will register you to the application so the id is known by the app.

## Run the Process 

Open the chat with your Bot and type `\newtask`.

Now you should receive a message in your group. Check the BPMN to see how the process works.

# Process
![Camunda BPMN][https://github.com/pme123/zio-scala-camunda-bot/raw/master/BPMN-bot-integration.png]

# ZIO ZLayers
![ZIO ZLayers][https://github.com/pme123/zio-scala-camunda-bot/raw/master/ZLayers.png]

# Development
## Mill
It uses Mill as its building tool.

### Update dependencies in Intellij

    mill mill.scalalib.GenIdea/idea