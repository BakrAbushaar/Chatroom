package Project.Server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class Room implements AutoCloseable{
    private String name;// unique name of the Room
    private volatile boolean isRunning = false;
    private ConcurrentHashMap<Long, ServerThread> clientsInRoom = new ConcurrentHashMap<Long, ServerThread>();
    private final Random random = new Random();

    public final static String LOBBY = "lobby";

    private void info(String message) {
        System.out.println(String.format("Room[%s]: %s", name, message));
    }

    public Room(String name) {
        this.name = name;
        isRunning = true;
        System.out.println(String.format("Room[%s] created", this.name));
    }

    public String getName() {
        return this.name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to add a client that already exists in the room");
            return;
        }
        clientsInRoom.put(client.getClientId(), client);
        client.setCurrentRoom(this);

        // notify clients of someone joining
        sendRoomStatus(client.getClientId(), client.getClientName(), true);
        // sync room state to joiner
        syncRoomList(client);

        info(String.format("%s[%s] joined the Room[%s]", client.getClientName(), client.getClientId(), getName()));

    }

    protected synchronized void removedClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        // notify remaining clients of someone leaving
        // happen before removal so leaving client gets the data
        sendRoomStatus(client.getClientId(), client.getClientName(), false);
        clientsInRoom.remove(client.getClientId());

        info(String.format("%s[%s] left the room", client.getClientName(), client.getClientId(), getName()));

        autoCleanup();

    }




    protected synchronized void connect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        long id = client.getClientId();
        connect(client);
       
        
        // Improved logging with user data
        info(String.format("%s[%s] connected", client.getClientName(), id));
    }
    /**
     * Takes a ServerThread and removes them from the Server
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param client
     */
    protected synchronized void disconnect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        long id = client.getClientId();
        sendDisconnect(client);
        client.disconnect();
        // removedClient(client); // <-- use this just for normal room leaving
        clientsInRoom.remove(client.getClientId());
        
        // Improved logging with user data
        info(String.format("%s[%s] disconnected", client.getClientName(), id));
    }

    protected synchronized void disconnectAll() {
        info("Disconnect All triggered");
        if (!isRunning) {
            return;
        }
        clientsInRoom.values().removeIf(client -> {
            disconnect(client);
            return true;
        });
        info("Disconnect All finished");
    }

    /**
     * Attempts to close the room to free up resources if it's empty
     */
    private void autoCleanup() {
        if (!Room.LOBBY.equalsIgnoreCase(name) && clientsInRoom.isEmpty()) {
            close();
        }
    }

    public void close() {
        // attempt to gracefully close and migrate clients
        if (!clientsInRoom.isEmpty()) {
            sendMessage(null, "Room is shutting down, migrating to lobby");
            info(String.format("migrating %s clients", name, clientsInRoom.size()));
            clientsInRoom.values().removeIf(client -> {
                Server.INSTANCE.joinRoom(Room.LOBBY, client);
                return true;
            });
        }
        Server.INSTANCE.removeRoom(this);
        isRunning = false;
        clientsInRoom.clear();
        info(String.format("closed", name));
    }

    // send/sync data to client(s)

    /**
     * Sends to all clients details of a disconnect client
     * @param client
     */
    protected synchronized void sendDisconnect(ServerThread client) {
        info(String.format("sending disconnect status to %s recipients", getName(), clientsInRoom.size()));
        clientsInRoom.values().removeIf(clientInRoom -> {
            boolean failedToSend = !clientInRoom.sendDisconnect(client.getClientId(), client.getClientName());
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Syncs info of existing users in room with the client
     * 
     * @param client
     */
    protected synchronized void syncRoomList(ServerThread client) {

        clientsInRoom.values().forEach(clientInRoom -> {
            if (clientInRoom.getClientId() != client.getClientId()) {
                client.sendClientSync(clientInRoom.getClientId(), clientInRoom.getClientName());
            }
        });
    }

    /**
     * Syncs room status of one client to all connected clients
     * 
     * @param clientId
     * @param clientName
     * @param isConnect
     */
    protected synchronized void sendRoomStatus(long clientId, String clientName, boolean isConnect) {
        info(String.format("sending room status to %s recipients", getName(), clientsInRoom.size()));
        clientsInRoom.values().removeIf(client -> {
            boolean failedToSend = !client.sendRoomAction(clientId, clientName, getName(), isConnect);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Sends a basic String message from the sender to all connectedClients
     * Internally calls processCommand and evaluates as necessary.
     * Note: Clients that fail to receive a message get removed from
     * connectedClients.
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param message
     * @param sender  ServerThread (client) sending the message or null if it's a
     *                server-generated message
     * bna24
     * November 27, 2024
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) {
            return;
        }
    
        String formattedMessage = formatText(message);
        long senderId = sender == null ? ServerThread.DEFAULT_CLIENT_ID : sender.getClientId();
    
        clientsInRoom.values().removeIf(client -> {
            if (sender != null && client.isMuted(sender.getClientName())) {
                info(String.format("Message from %s to %s skipped (muted).", sender.getClientName(), client.getClientName()));
                return false; // Skip but don't remove the client
            }
    
            boolean failedToSend = !client.sendMessage(senderId, formattedMessage);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }
    
    
    
    //bna24
    //November 27, 2024
    protected synchronized void sendPrivateMessage(ServerThread sender, long targetClientId, String message) {
        if (!isRunning) {
            return;
        }
    
        ServerThread target = clientsInRoom.get(targetClientId);
    
        if (target != null) {
            if (target.isMuted(sender.getClientName())) {
                info(String.format("Private message from %s to %s skipped (muted).", sender.getClientName(), target.getClientName()));
                sender.sendMessage(String.format("Your private message to %s was not delivered (you are muted).", target.getClientName()));
                return;
            }
    
            String formattedMessageToSender = String.format("[PRIVATE] To %s: %s", target.getClientName(), message);
            String formattedMessageToReceiver = String.format("[PRIVATE] From %s: %s", sender.getClientName(), message);
    
            sender.sendMessage(formattedMessageToSender);
            target.sendMessage(formattedMessageToReceiver);
    
            info(String.format("Private message from %s to %s: %s", sender.getClientName(), target.getClientName(), message));
        } else {
            sender.sendMessage(String.format("User with ID %d not found.", targetClientId));
        }
    }
    
    
    // end send data to client(s)





    // receive data from ServerThread
    //bna24
    //10/20/2024
    protected void handleCreateRoom(ServerThread sender, String room) {
        if (Server.INSTANCE.createRoom(room)) {
            Server.INSTANCE.joinRoom(room, sender);
        } else {
            sender.sendMessage(String.format("Room %s already exists", room));
        }
    }
    
    //bna24
    //10/20/2024
    protected void handleJoinRoom(ServerThread sender, String room) {
        if (!Server.INSTANCE.joinRoom(room, sender)) {
            sender.sendMessage(String.format("Room %s doesn't exist", room));
        }
    }

    protected void clientDisconnect(ServerThread sender) {
        disconnect(sender);
    }

    public void handleClientConnect(ServerThread client, String clientName) {
        client.setClientName(clientName);
        addClient(client); 
        System.out.println("Client connected to room: " + getName());
    }

    //bna24
    //november 27, 2024 (milestone4)
    protected synchronized void handleMute(ServerThread sender, long targetClientId) {
        ServerThread target = clientsInRoom.get(targetClientId);
        if (target != null) {
            if (sender.addToMuteList(target.getClientName())) { 
                info(String.format("%s muted %s", sender.getClientName(), target.getClientName()));
                sender.sendMessage(String.format("You have muted %s", target.getClientName()));
                target.sendMessage(String.format("%s has muted you.", sender.getClientName()));
            } else {
                sender.sendMessage(String.format("%s is already muted.", target.getClientName()));
            }
        } else {
            sender.sendMessage(String.format("Client with ID %d not found to mute.", targetClientId));
        }
    }

    protected synchronized void handleUnmute(ServerThread sender, long targetClientId) {
        ServerThread target = clientsInRoom.get(targetClientId);
        if (target != null) {
            if (sender.removeFromMuteList(target.getClientName())) { 
                info(String.format("%s unmuted %s", sender.getClientName(), target.getClientName()));
                sender.sendMessage(String.format("You have unmuted %s", target.getClientName()));
                target.sendMessage(String.format("%s has unmuted you.", sender.getClientName()));
            } else {
                sender.sendMessage(String.format("%s is not currently muted.", target.getClientName()));
            }
        } else {
            sender.sendMessage(String.format("Client with ID %d not found to unmute.", targetClientId));
        }
    }




//  Text Formatting
//  bna24
//  november 11, 2024
private String formatText(String message) {
    String boldPattern = "\\*\\*(.*?)\\*\\*";
    String italicPattern = "\\*(.*?)\\*";
    String underlinePattern = "_(.*?)_";
    String redPattern = "#r(.*?)r#";
    String greenPattern = "#g(.*?)g#";
    String bluePattern = "#b(.*?)b#";   

    //replaces with HTML
    message = message.replaceAll(boldPattern, "<b>$1</b>");
    message = message.replaceAll(italicPattern, "<i>$1</i>");
    message = message.replaceAll(underlinePattern, "<u>$1</u>");
    message = message.replaceAll(redPattern, "<span style=\"color:red;\">$1</span>");
    message = message.replaceAll(greenPattern, "<span style=\"color:green;\">$1</span>");
    message = message.replaceAll(bluePattern, "<span style=\"color:blue;\">$1</span>");
    return message;
}



//bna24
//November 11, 2024
   public void handleRoll(ServerThread sender, int diceCount, int diceSides) {
    String clientName = sender.getClientName();
    String resultMessage;

    if (diceSides > 0) {
        if (diceCount == 1) {
            int result = random.nextInt(diceSides) + 1;
            resultMessage = String.format("%s rolled %d and got #b%db#", clientName, diceSides, result);
        } else {
            int total = 0;
            for (int i = 0; i < diceCount; i++) {
                int roll = random.nextInt(diceSides) + 1;
                total += roll;
            }
            resultMessage = String.format("%s rolled %dd%d and got #b%db#", clientName, diceCount, diceSides, total);
        }
        broadcastMessage(sender, formatText(resultMessage)); 
    } else {
        sender.sendMessage("Invalid roll command parameters.");
    }
}
    




   public void handleFlip(ServerThread sender) {
    String clientName = sender.getClientName();
    String result = random.nextBoolean() ? "#gheadsg#" : "#gtailsg#";
    String resultMessage = String.format("%s flipped a coin and got %s", clientName, result);

    broadcastMessage(sender, formatText(resultMessage)); 
}
    


//bna24
//November 11, 2024
    private void broadcastMessage(ServerThread sender, String message) {
    long senderId = sender.getClientId();
    clientsInRoom.values().forEach(client -> client.sendMessage(senderId, message));
}
}
    
    


    // end receive data from ServerThread
