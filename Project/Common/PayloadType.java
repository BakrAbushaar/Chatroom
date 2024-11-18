package Project.Common;

public enum PayloadType {
    CLIENT_CONNECT, // client requesting to connect to server (passing of initialization data [name])
    CLIENT_ID,  // server sending client id
    SYNC_CLIENT,  // silent syncing of clients in room
    DISCONNECT,  // distinct disconnect action
    ROOM_CREATE,
    ROOM_JOIN, // join/leave room based on boolean
    
    ROOM_LIST,
    //bna24
    // November 11, 2024
    ROLL, // roll commands dor dice
    FLIP, // flip for coin toss


    MESSAGE // sender and message
}