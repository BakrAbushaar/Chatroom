package Project.Server;

//import java.awt.List;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

import Project.Common.ConnectionPayload;
import Project.Common.FlipPayLoad;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RollPayload;
import Project.Client.CardView;
import java.util.HashSet;
import java.util.Set;
import java.util.List;


/**
 * A server-side representation of a single client.
 * This class is more about the data and abstracted communication
 */
public class ServerThread extends BaseServerThread {
    public static final long DEFAULT_CLIENT_ID = -1;
    private Room currentRoom;
    private long clientId;
    private String clientName;
    private Consumer<ServerThread> onInitializationComplete; // callback to inform when this object is ready

    /**
     * Wraps the Socket connection and takes a Server reference and a callback
     * 
     * @param myClient
     * @param server
     * @param onInitializationComplete method to inform listener that this object is
     *                                 ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        // get communication channels to single client
        this.client = myClient;
        this.clientId = ServerThread.DEFAULT_CLIENT_ID;// this is updated later by the server
        this.onInitializationComplete = onInitializationComplete;

    }

    public void setClientName(String name) {
        if (name == null) {
            throw new NullPointerException("Client name can't be null");
        }
        this.clientName = name;
        onInitialized();
    }
    public String getClientName(){
        return clientName;
    }

    public long getClientId() {
        return this.clientId;
    }

    protected Room getCurrentRoom() {
        return this.currentRoom;
    }

    protected void setCurrentRoom(Room room) {
        if (room == null) {
            throw new NullPointerException("Room argument can't be null");
        }
        currentRoom = room;
    }

    // Mute and Unmute (saveMuteList() milestone4)
    private Set<String> mutedUsers = new HashSet<>();
    public boolean isMuted(String username) {
        return mutedUsers.contains(username);
    }

    //milestone4
    public boolean addToMuteList(String username) {
        if (mutedUsers.add(username)) { 
            info("Client " + clientName + " muted " + username); 
            return true; 
        } else {
            info("Mute request ignored. " + username + " is already muted."); 
            return false; 
        }
    }
    
    public boolean removeFromMuteList(String username) {
        if (mutedUsers.remove(username)) { 
            info("Client " + clientName + " unmuted " + username); 
            return true; 
        } else {
            info("Unmute request ignored. " + username + " is not muted."); 
            return false;
        }
    }
    


    @Override
    protected void onInitialized() {
        System.out.println("ServerThread.onInitialized called");
        onInitializationComplete.accept(this); // Notify server that initialization is complete
    }

    @Override
    protected void info(String message) {
        System.out.println(String.format("ServerThread[%s(%s)]: %s", getClientName(), getClientId(), message));
    }

    @Override
    protected void cleanup() {
        currentRoom = null;
        super.cleanup();
    }
    
    @Override
    protected void disconnect(){
        //sendDisconnect(clientId, clientName);
        super.disconnect();
    }
    // handle received message from the Client
    @Override
    //bna24
    //November 27 2024
    protected void processPayload(Payload payload) {
        try {
            switch (payload.getPayloadType()) {
                case CLIENT_CONNECT:
                    ConnectionPayload connectionPayload = (ConnectionPayload) payload;
                    setClientName(connectionPayload.getClientName());
                    loadMuteList();
                    System.out.println("Client connected with name: " + connectionPayload.getClientName());
                    sendClientId(this.clientId);
                break;
                case MESSAGE:
                    currentRoom.sendMessage(this, payload.getMessage());
                    break;
                case ROOM_CREATE:
                    currentRoom.handleCreateRoom(this, payload.getMessage());
                    break;
                case ROOM_JOIN:
                    currentRoom.handleJoinRoom(this, payload.getMessage());
                    break;
                case DISCONNECT:
                    currentRoom.disconnect(this);
                    break;
                
                // bna24
                //november 11,2024
                case ROLL:
                    RollPayload rollPayload = (RollPayload) payload;
                    currentRoom.handleRoll(this, rollPayload.getDiceCount(), rollPayload.getDiceSides());
                    break;
                // bna24
                // November 11, 2024
                case FLIP:
                    currentRoom.handleFlip(this);
                    break;

                case PRIVATE_MESSAGE:
                    long targetClientId = payload.getTargetClientId();
                    String privateMessage = payload.getMessage();
                    currentRoom.sendPrivateMessage(this, targetClientId, privateMessage);
                    break;

                case MUTE:
                    currentRoom.handleMute(this, payload.getTargetClientId());
                    break;
    
                case UNMUTE:
                    currentRoom.handleUnmute(this, payload.getTargetClientId());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Could not process Payload: " + payload);
            e.printStackTrace();
        }
    }

   //4thimp new
    public boolean sendMuteStatus(long targetClientId, boolean isMuted) {
        Payload p = new Payload();
        p.setPayloadType(isMuted ? PayloadType.MUTE : PayloadType.UNMUTE);
        p.setTargetClientId(targetClientId);
        return send(p);
    }





    //Milestone4
    private void loadMuteList() {
        try {
            String filename = clientName + "_mute_list.txt";
            Path filePath = Paths.get(filename);
            if (Files.exists(filePath)) {
                java.util.List<String> loadedMuteList = Files.readAllLines(filePath);
                mutedUsers.addAll(loadedMuteList);
                info("Loaded mute list for " + clientName + ": " + loadedMuteList);
            } else {
                info("No mute list found for " + clientName + ", starting fresh.");
            }
        } catch (IOException e) {
            System.err.println("Error loading mute list for " + clientName + ": " + e.getMessage());
        }
    }
    
    private void saveMuteList() {
        try {
            String filename = clientName + "_mute_list.txt";
            Path filePath = Paths.get(filename);
            Files.write(filePath, new java.util.ArrayList<>(mutedUsers)); 
            info("Saved mute list for " + clientName);
        } catch (IOException e) {
            System.err.println("Error saving mute list for " + clientName + ": " + e.getMessage());
        }
    }


    // send methods to pass data back to the Client

    public boolean sendClientSync(long clientId, String clientName){
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        cp.setConnect(true);
        cp.setPayloadType(PayloadType.SYNC_CLIENT);
        return send(cp);
    }


    public boolean sendPrivateMessage(long senderId, long recipientId, String message) {
        
        Payload privateMessagePayload = new Payload();
        privateMessagePayload.setPayloadType(PayloadType.PRIVATE_MESSAGE); 
        privateMessagePayload.setClientId(senderId);                       
        privateMessagePayload.setTargetClientId(recipientId);             
        privateMessagePayload.setMessage(message);                         
    
        return send(privateMessagePayload);
    }


    
    /**
     * Overload of sendMessage used for server-side generated messages
     * 
     * @param message
     * @return @see {@link #send(Payload)}
     */
    public boolean sendMessage(String message) {
        return sendMessage(ServerThread.DEFAULT_CLIENT_ID, message);
    }

    /**
     * Sends a message with the author/source identifier
     * 
     * @param senderId
     * @param message
     * @return @see {@link #send(Payload)}
     */
    public boolean sendMessage(long senderId, String message) {
        Payload p = new Payload();
        p.setClientId(senderId);
        p.setMessage(message);
        p.setPayloadType(PayloadType.MESSAGE);
        return send(p);
    }

    /**
     * Tells the client information about a client joining/leaving a room
     * 
     * @param clientId   their unique identifier
     * @param clientName their name
     * @param room       the room
     * @param isJoin     true for join, false for leaivng
     * @return success of sending the payload
     */
    public boolean sendRoomAction(long clientId, String clientName, String room, boolean isJoin) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.ROOM_JOIN);
        cp.setConnect(isJoin); //<-- determine if join or leave
        cp.setMessage(room);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    /**
     * Tells the client information about a disconnect (similar to leaving a room)
     * 
     * @param clientId   their unique identifier
     * @param clientName their name
     * @return success of sending the payload
     */
    public boolean sendDisconnect(long clientId, String clientName) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.DISCONNECT);
        cp.setConnect(false);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    /**
     * Sends (and sets) this client their id (typically when they first connect)
     * 
     * @param clientId
     * @return success of sending the payload
     */
    public boolean sendClientId(long clientId) {
        this.clientId = clientId;
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.CLIENT_ID);
        cp.setConnect(true);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    // end send methods



    public boolean sendRoll(long clientId, int diceCount, int diceSides) {
        RollPayload rollPayload = new RollPayload(diceCount, diceSides);
        rollPayload.setPayloadType(PayloadType.ROLL);   
        rollPayload.setClientId(clientId);              
        rollPayload.setDiceCount(diceCount);            
        rollPayload.setDiceSides(diceSides);            
        
        return send(rollPayload);                       
    }
    public boolean sendFlip(long clientId) {
        FlipPayLoad flipPayload = new FlipPayLoad();
        flipPayload.setPayloadType(PayloadType.FLIP);   
        flipPayload.setClientId(clientId);             
        flipPayload.setMessage(clientName);             
        
        return send(flipPayload);                      
    }



   
    
}
