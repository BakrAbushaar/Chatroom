package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Project.Client.CardView;
import Project.Client.Interfaces.IConnectionEvents;
import Project.Client.Interfaces.IClientEvents;
import Project.Client.Interfaces.IMessageEvents;
import Project.Client.Interfaces.IRoomEvents;
import Project.Common.TextFX.Color;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.ConnectionPayload;
import Project.Common.TextFX;
import Project.Common.RollPayload;
import Project.Common.FlipPayLoad;
import Project.Common.LoggerUtil;

/**
 * Demoing bi-directional communication between client and server in a
 * multi-client scenario
 */


 
public enum Client {
    INSTANCE;



{
        // TODO moved to ClientUI (this repeat doesn't do anything since config is set
        // only once)
        // statically initialize the client-side LoggerUtil
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // 2MB
        config.setFileCount(1);
        config.setLogLocation("client.log");
        // Set the logger configuration
        LoggerUtil.INSTANCE.setConfig(config);
    }



    
    private Socket server = null;
    private ObjectOutputStream out = null;
    private ObjectInputStream in = null;
    final Pattern ipAddressPattern = Pattern
            .compile("/connect\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{3,5})");
    final Pattern localhostPattern = Pattern.compile("/connect\\s+(localhost:\\d{3,5})");
    private volatile boolean isRunning = true; // volatile for thread-safe visibility
    private ConcurrentHashMap<Long, ClientData> knownClients = new ConcurrentHashMap<>();
    private ClientData myData;


    // constants (used to reduce potential types when using them in code)
    private final String COMMAND_CHARACTER = "/";
    private final String CREATE_ROOM = "createroom";
    private final String JOIN_ROOM = "joinroom";
    private final String DISCONNECT = "disconnect";
    private final String LOGOFF = "logoff";
    private final String LOGOUT = "logout";
    private final String SINGLE_SPACE = " ";
    private final String ROLL = "roll";
    private final String FLIP = "flip";

    private static IClientEvents events;
    
    // needs to be private now that the enum logic is handling this
    private Client() {
        System.out.println("Client Created");
        myData = new ClientData();
    }

    public boolean isConnected() {
        if (server == null) {
            return false;
        }
        // https://stackoverflow.com/a/10241044
        // Note: these check the client's end of the socket connect; therefore they
        // don't really help determine if the server had a problem
        // and is just for lesson's sake
        return server.isConnected() && !server.isClosed() && !server.isInputShutdown() && !server.isOutputShutdown();
    }

    /**
     * Takes an IP address and a port to attempt a socket connection to a server.
     * 
     * @param address
     * @param port
     * @return true if connection was successful
     */
     private boolean connect(String address, int port) {
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            sendConnect();
            // Use CompletableFuture to run listenToServer() in a separate thread
            CompletableFuture.runAsync(this::listenToServer);
        } catch (UnknownHostException e) {
            LoggerUtil.INSTANCE.warning("Unknown host", e);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("IOException", e);
        }
        LoggerUtil.INSTANCE.info("THIS IS THE PORT AND ADDRESS" + port + " " + address);
        return isConnected();
    }




    public boolean connect(String address, int port, String username, IClientEvents callback) {
        myData.setClientName(username);
        Client.events = callback;
        System.out.println("Client events set to: " + callback.getClass().getName());
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            sendConnect();
            // Use CompletableFuture to run listenToServer() in a separate thread
            CompletableFuture.runAsync(this::listenToServer);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isConnected();
    }
    /**
     * <p>
     * Check if the string contains the <i>connect</i> command
     * followed by an IP address and port or localhost and port.
     * </p>
     * <p>
     * Example format: 123.123.123.123:3000
     * </p>
     * <p>
     * Example format: localhost:3000
     * </p>
     * https://www.w3schools.com/java/java_regex.asp
     * 
     * @param text
     * @return true if the text is a valid connection command
     */
    private boolean isConnection(String text) {
        Matcher ipMatcher = ipAddressPattern.matcher(text);
        Matcher localhostMatcher = localhostPattern.matcher(text);
        return ipMatcher.matches() || localhostMatcher.matches();
    }

    /**
     * Controller for handling various text commands.
     * <p>
     * Add more here as needed
     * </p>
     * 
     * @param text
     * @return true if the text was a command or triggered a command
     */
     private boolean processClientCommand(String text) throws IOException {
        if (isConnection(text)) {
            if (myData.getClientName() == null || myData.getClientName().length() == 0) {
                System.out.println(TextFX.colorize("Name must be set first via /name command", Color.RED));
                return true;
            }
            // replaces multiple spaces with a single space
            // splits on the space after connect (gives us host and port)
            // splits on : to get host as index 0 and port as index 1
            String[] parts = text.trim().replaceAll(" +", " ").split(" ")[1].split(":");
            connect(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            sendClientName();
            return true;
        } else if ("/quit".equalsIgnoreCase(text)) {
            close();
            return true;
        } else if (text.startsWith("/name")) {
            myData.setClientName(text.replace("/name", "").trim());
            System.out.println(TextFX.colorize("Set client name to " + myData.getClientName(), Color.CYAN));
            return true;
        } else if (text.equalsIgnoreCase("/users")) {
            System.out.println(
                    String.join("\n", knownClients.values().stream()
                            .map(c -> String.format("%s(%s)", c.getClientName(), c.getClientId())).toList()));
            return true;
        } else { // logic previously from Room.java
            // decided to make this as separate block to separate the core client-side items
            // vs the ones that generally are used after connection and that send requests
            if (text.startsWith(COMMAND_CHARACTER)) {
                boolean wasCommand = false;
                String fullCommand = text.replace(COMMAND_CHARACTER, "");
                String part1 = fullCommand;
                String[] commandParts = part1.split(SINGLE_SPACE, 2);// using limit so spaces in the command value
                                                                     // aren't split
                final String command = commandParts[0];
                final String commandValue = commandParts.length >= 2 ? commandParts[1] : "";
                switch (command) {
                    case CREATE_ROOM:
                        sendCreateRoom(commandValue);
                        wasCommand = true;
                        break;
                    case JOIN_ROOM:
                        sendJoinRoom(commandValue);
                        wasCommand = true;
                        break;
                    // Note: these are to disconnect, they're not for changing rooms
                    case DISCONNECT:
                    case LOGOFF:
                    case LOGOUT:
                        sendDisconnect();
                        wasCommand = true;
                        break;
                     //bna24
                    //November 11, 2024
                    case ROLL:
                        try {
                            if (!commandValue.contains("d")) { 
                                int range = Integer.parseInt(commandValue); 
                                sendRollCommand(1, range);  // Single roll 
                            } else { 
                                String[] parts = commandValue.split("d");
                                if (parts.length == 2) {
                                    int diceCount = Integer.parseInt(parts[0]);
                                    int diceSides = Integer.parseInt(parts[1]);
                                    sendRollCommand(diceCount, diceSides);  // Multiple dice 
                                } else {
                                    System.out.println(TextFX.colorize("Invalid command format, try /roll #d#", Color.RED));
                                }
                            }
                        } catch (Exception e) {
                            System.out.println(TextFX.colorize("Invalid command format, try /roll # or /roll #d#", Color.RED));
                        }
                        wasCommand = true;
                        break;
                    //bna24
                    //November 11, 2024
                    case FLIP:
                        sendFlipCommand();  
                        wasCommand = true;
                        break;
                        
                
                }
                return wasCommand;
            }
        }
        return false;
    }

    // send methods to pass data to the ServerThread
    public long getMyClientId() {
        return myData.getClientId();
    }

    public void sendListRooms(String roomQuery) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_LIST);
        p.setMessage(roomQuery);
        send(p);
    }



    /**
     * Sends the room name we intend to create
     * 
     * @param room
     */

    public void sendCreateRoom(String room) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_CREATE);
        p.setMessage(room);
        send(p);
    }

    /**
     * Sends the room name we intend to join
     * 
     * @param room
     */
    public void sendJoinRoom(String room) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_JOIN);
        p.setMessage(room);
        send(p);
    }


    public void sendConnect() throws IOException {
        ConnectionPayload p = new ConnectionPayload();
        p.setPayloadType(PayloadType.CONNECT);
        p.setClientName(myData.getClientName());
        p.setConnect(true);
        System.out.println("client.sendConnect()  Connection payload.CONNECT sent with name: " + myData.getClientName());
        send(p);
    }
        
    


    /**
     * Tells the server-side we want to disconnect
     */
    void sendDisconnect() throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.DISCONNECT);
        send(p);
    }
    /**
     * Sends desired message over the socket
     * 
     * @param message
     */
    public void sendMessage(String message) throws IOException {
        if (processClientCommand(message)) {
            return;
        }
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MESSAGE);
        p.setMessage(message);
        send(p);
    }

    /**
     * Sends chosen client name after socket handshake
     */
    private void sendClientName() throws IOException {
        if (myData.getClientName() == null || myData.getClientName().length() == 0) {
            System.out.println(TextFX.colorize("Name must be set first via /name command", Color.RED));
            return;
        }
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientName(myData.getClientName());
        send(cp);
    }


    
    private void sendRollCommand(int diceCount, int diceSides) {
        RollPayload rollPayload  = new RollPayload(diceCount, diceSides);
        send(rollPayload );
    }
    /**
     * bna24
     * November 11, 2024
    */
    private void sendFlipCommand() {
        FlipPayLoad flipPayload  = new FlipPayLoad();
        send(flipPayload );
    }
    
    /**
     * Generic send that passes any Payload over the socket (to ServerThread)
     * 
     * @param p
     */
    private void send(Payload p) {
        try {
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    // end send methods

    public void start() throws IOException {
        System.out.println("Client starting");

        // Use CompletableFuture to run listenToInput() in a separate thread
        CompletableFuture<Void> inputFuture = CompletableFuture.runAsync(this::listenToInput);

        // Wait for inputFuture to complete to ensure proper termination
        inputFuture.join();
    }

    /**
     * Listens for messages from the server
     */
    private void listenToServer() {
        try {
            while (isRunning && isConnected()) {
                Payload fromServer = (Payload) in.readObject(); // blocking read
                if (fromServer != null) {
                System.out.println("Client received payload: " + fromServer.getPayloadType());

                    processPayload(fromServer);
                } else {
                    System.out.println("Server disconnected");
                    break;
                }
            }
        } catch (ClassCastException | ClassNotFoundException cce) {
            System.err.println("Error reading object as specified type: " + cce.getMessage());
            cce.printStackTrace();
        } catch (IOException e) {
            if (isRunning) {
                System.out.println("Connection dropped");
                e.printStackTrace();
            }
        } finally {
            closeServerConnection();
        }
        System.out.println("listenToServer thread stopped");
    }

    /**
     * Listens for keyboard input from the user
     */
    private void listenToInput() {
        try (Scanner si = new Scanner(System.in)) {
            System.out.println("Waiting for input"); // moved here to avoid console spam
            while (isRunning) { // Run until isRunning is false
                String line = si.nextLine();
                if (!processClientCommand(line)) {
                    if (isConnected()) {
                        sendMessage(line);
                    } else {
                        System.out.println(
                                "Not connected to server (hint: type `/connect host:port` without the quotes and replace host/port with the necessary info)");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error in listentToInput()");
            e.printStackTrace();
        }
        System.out.println("listenToInput thread stopped");
    }

    /**
     * Closes the client connection and associated resources
     */
    private void close() {
        isRunning = false;
        closeServerConnection();
        System.out.println("Client terminated");
        // System.exit(0); // Terminate the application
    }

    /**
     * Closes the server connection and associated resources
     */
    private void closeServerConnection() {
        myData.reset();
        knownClients.clear();
        try {
            if (out != null) {
                System.out.println("Closing output stream");
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (in != null) {
                System.out.println("Closing input stream");
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (server != null) {
                System.out.println("Closing connection");
                server.close();
                System.out.println("Closed socket");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = Client.INSTANCE;
        try {
            client.start();
        } catch (IOException e) {
            System.out.println("Exception from main()");
            e.printStackTrace();
        }
    }

    /**
     * Handles received message from the ServerThread
     * 
     * @param payload
     */
    private void processPayload(Payload payload) {
        try {
            switch (payload.getPayloadType()) {
                case PayloadType.CLIENT_ID: // get id assigned
                System.out.println("CLIENT_ID payload received --- ProcessPayload");
                    ConnectionPayload cp = (ConnectionPayload) payload;
                    processClientData(cp.getClientId(), cp.getClientName());
                        if (Client.events != null && Client.events instanceof IConnectionEvents) {
                            ((IConnectionEvents) Client.events).onReceiveClientId(cp.getClientId());
                            System.out.println("onReceiveClientId called with ID: " + cp.getClientId());
                    }
                    break;
                case PayloadType.SYNC_CLIENT: // silent add
                    cp = (ConnectionPayload) payload;
                    processClientSync(cp.getClientId(), cp.getClientName());
                    break;
                case PayloadType.DISCONNECT: // remove a disconnected client (mostly for the specific message vs leaving
                                             // a room)
                    cp = (ConnectionPayload) payload;
                    processDisconnect(cp.getClientId(), cp.getClientName());
                    // note: we want this to cascade
                 case PayloadType.CONNECT:
                    cp = (ConnectionPayload) payload;
                    processConnect(cp.getClientId(), cp.getClientName(), cp.getMessage());
                    break;
                case PayloadType.ROOM_JOIN: // add/remove client info from known clients
                    cp = (ConnectionPayload) payload;
                    processRoomAction(cp.getClientId(), cp.getClientName(), cp.getMessage(), cp.isConnect());
                    break;
                case PayloadType.MESSAGE: // displays a received message
                    processMessage(payload.getClientId(), payload.getMessage());
                    break;
                case PayloadType.ROLL: // handle roll command
                     RollPayload rollPayload = (RollPayload) payload;
                     processRoll(rollPayload.getClientId(), rollPayload.getDiceCount(), rollPayload.getDiceSides());
                    break;
                case PayloadType.FLIP: // handle flip command
                    FlipPayLoad flipPayload = (FlipPayLoad) payload;
                    processFlip(flipPayload.getClientId());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Could not process Payload: " + payload);
            e.printStackTrace();
        }
    }



    public String getClientNameFromId(long id) {
        if (id == ClientData.DEFAULT_CLIENT_ID) {
            return "Room";
        }
        if (knownClients.containsKey(id)) {
            return knownClients.get(id).getClientName();
        }
        return "[Unknown]";
    }




    // payload processors
    // payload processors

    private void processConnect(long clientId, String clientName, String roomName) {
    // Check if the client is already in the known clients list
    if (!knownClients.containsKey(clientId)) {
        // Add the client to the known clients list
        ClientData clientData = new ClientData();
        clientData.setClientId(clientId);
        clientData.setClientName(clientName);
        knownClients.put(clientId, clientData);

        // Print confirmation of the connection
        System.out.println(TextFX.colorize(
            String.format("*%s[%s] connected to room %s*", clientName, clientId, roomName),
            Color.GREEN
        ));

        // Notify via IRoomEvents
        ((IRoomEvents) events).onRoomAction(clientId, clientName, roomName, true);
    }
}


    private void processRoomsList(List<String> rooms, String message) {
        // invoke onReceiveRoomList callback
        ((IRoomEvents) events).onReceiveRoomList(rooms, message);
        if (rooms == null || rooms.size() == 0) {
            System.out.println(
                    TextFX.colorize("No rooms found matching your query",
                            Color.RED));
            return;
        }
        System.out.println(TextFX.colorize("Room Results:", Color.PURPLE));
        System.out.println(
                String.join("\n", rooms));

    }




    private void processDisconnect(long clientId, String clientName) {
        // invoke onClientDisconnect callback
        ((IConnectionEvents) events).onClientDisconnect(clientId, clientName);
        System.out.println(
                TextFX.colorize(String.format("*%s disconnected*",
                        clientId == myData.getClientId() ? "You" : clientName),
                        Color.RED));
        if (clientId == myData.getClientId()) {
            closeServerConnection();
        }
    }

    private void processClientData(long clientId, String clientName) {
        if (myData.getClientId() == ClientData.DEFAULT_CLIENT_ID) {
            myData.setClientId(clientId);
            myData.setClientName(clientName);
            // knownClients.put(cp.getClientId(), myData);// <-- this is handled later
        }
    }

     private void processMessage(long clientId, String message) {
        String name = knownClients.containsKey(clientId) ? knownClients.get(clientId).getClientName() : "Room";
        System.out.println(TextFX.colorize(String.format("%s: %s", name, message), Color.BLUE));
        // invoke onMessageReceive callback
        ((IMessageEvents) events).onMessageReceive(clientId, message);
    }

    private void processClientSync(long clientId, String clientName) {

        if (!knownClients.containsKey(clientId)) {
            ClientData cd = new ClientData();
            cd.setClientId(clientId);
            cd.setClientName(clientName);
            knownClients.put(clientId, cd);
            // invoke onSyncClient callback
            ((IConnectionEvents) events).onSyncClient(clientId, clientName);
        }
    }

   private void processRoomAction(long clientId, String clientName, String message, boolean isJoin) {

        if (isJoin && !knownClients.containsKey(clientId)) {
            ClientData cd = new ClientData();
            cd.setClientId(clientId);
            cd.setClientName(clientName);
            knownClients.put(clientId, cd);
            System.out.println(TextFX
                    .colorize(String.format("*%s[%s] joined the Room %s*", clientName, clientId, message),
                            Color.GREEN));
            // invoke onRoomJoin callback
            ((IRoomEvents) events).onRoomAction(clientId, clientName, message, isJoin);
        } else if (!isJoin) {
            ClientData removed = knownClients.remove(clientId);
            if (removed != null) {
                System.out.println(
                        TextFX.colorize(String.format("*%s[%s] left the Room %s*", clientName, clientId, message),
                                Color.YELLOW));
                // invoke onRoomJoin callback
                ((IRoomEvents) events).onRoomAction(clientId, clientName, message, isJoin);
            }
            // clear our list
            if (clientId == myData.getClientId()) {
                knownClients.clear();
                // invoke onResetUserList()
                ((IConnectionEvents) events).onResetUserList();
            }
        }
    }
    // end payload processors

    private void processRoll(long clientId, int diceCount, int diceSides) { 
        ClientData cp = new ClientData();
        cp.setClientId(clientId);
        RollPayload rollPayload = new RollPayload(diceCount, diceSides);
        
        rollPayload.setDiceCount(diceCount);
        rollPayload.setDiceSides(diceSides);
        
        System.out.println(TextFX.colorize(String.format("%s Set Dice Count and Sides %d, %d", cp.getClientName(), diceCount, diceSides), Color.CYAN));
    }
    
    private void processFlip(long clientId) {
        ClientData cp = new ClientData();
        cp.setClientId(clientId);
        FlipPayLoad fp = new FlipPayLoad();
        System.out.println(TextFX.colorize(String.format(cp.getClientName()), Color.CYAN));

    }
}
