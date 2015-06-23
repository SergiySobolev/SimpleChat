package com.sbk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class Server {

    Logger log = LoggerFactory.getLogger(getClass());

    private static int uniqueId;

    private List<ClientThread> clientThreads;

    private ServerGUI sg;

    private SimpleDateFormat sdf;

    private int port;

    private boolean keepGoing;

    public Server(int port) {
        this(port, null);
    }

    public Server(int port, ServerGUI gui){
        this.port = port;
        this.sg = gui;
        sdf = new SimpleDateFormat("HH:mm:ss");
        clientThreads = newArrayList();
    }

    public void start() {
        keepGoing = true;
        try {
            ServerSocket serverSocket = new ServerSocket(port);

            while(keepGoing) {
                log.info("Server waits for clients on port {}.", port);
                Socket socket = serverSocket.accept();
                if(!keepGoing) {
                    break;
                }
                ClientThread t = new ClientThread(socket);
                clientThreads.add(t);
                t.start();
            }

            try {
                serverSocket.close();
                for(int i = 0; i < clientThreads.size(); ++i) {
                    ClientThread tc = clientThreads.get(i);
                    try {
                        tc.sInput.close();
                        tc.sOutput.close();
                        tc.socket.close();
                    }
                    catch(IOException ioE) {
                        log.error("Error while closing client thread");
                    }
                }
            } catch (Exception e){
                log.error("Exception on new server socket");
            }
        }catch (Exception e){

        }
    }

    public void stop() {
        keepGoing = false;
        try {
            new Socket("localhost", port);
        }
        catch(Exception e) {
        }
    }

    private void display(String msg) {
        String time = sdf.format(new Date()) + " " + msg;
        if(sg == null) {
            System.out.println(time);
        }  else {
            sg.appendEvent(time + "\n");
        }
    }

    private synchronized void broadcast(String message) {
        String time = sdf.format(new Date());
        String messageLf = time + " " + message + "\n";
        if(sg == null)
            System.out.print(messageLf);
        else
            sg.appendRoom(messageLf);
        for(int i = clientThreads.size(); --i >= 0;) {
            ClientThread ct = clientThreads.get(i);
            if(!ct.writeMsg(messageLf)) {
                clientThreads.remove(i);
                display("Disconnected Client " + ct.username + " removed from list.");
            }
        }
    }

    synchronized void remove(int id) {
        for(int i = 0; i < clientThreads.size(); ++i) {
            ClientThread ct = clientThreads.get(i);
            if(ct.id == id) {
                clientThreads.remove(i);
                return;
            }
        }
    }

    public static void main(String[] args) {
        int portNumber = 1500;
        switch(args.length) {
            case 1:
                try {
                    portNumber = Integer.parseInt(args[0]);
                }
                catch(Exception e) {
                    System.out.println("Invalid port number.");
                    System.out.println("Usage is: > java Server [portNumber]");
                    return;
                }
            case 0:
                break;
            default:
                System.out.println("Usage is: > java Server [portNumber]");
                return;

        }
        Server server = new Server(portNumber);
        server.start();
        System.out.print("Server started");
    }

    class ClientThread extends Thread {
        Socket socket;
        ObjectInputStream sInput;
        ObjectOutputStream sOutput;
        int id;
        String username;
        ChatMessage cm;
        String date;

        ClientThread(Socket s){
            id = ++uniqueId;
            this.socket = s;
            log.info("Thread trying to create Object Input/Output Streams");
            try {
                sOutput = new ObjectOutputStream(socket.getOutputStream());
                sInput  = new ObjectInputStream(socket.getInputStream());
                username = (String) sInput.readObject();
            } catch (IOException e){
                log.error("Exception creating new Input/output Streams:{} ", e.getMessage());
                display(String.format("Exception creating new Input/output Streams:%s " , e.getMessage()));
            } catch (ClassNotFoundException e) {
                log.error("Can't get username: {}", e.getMessage());
                display(String.format("Can't get username: %s " , e.getMessage()));
            }
            date = new Date().toString() + "\n";
        }

        public void run() {
            boolean keepGoing = true;
            while(keepGoing) {
                try {
                    cm = (ChatMessage) sInput.readObject();
                }
                catch (IOException e) {
                    display(username + " Exception reading Streams: " + e);
                    break;
                }
                catch(ClassNotFoundException e2) {
                    break;
                }
                String message = cm.getMessage();

                switch(cm.getType()) {

                    case ChatMessage.MESSAGE:
                        broadcast(username + ": " + message);
                        break;
                    case ChatMessage.LOGOUT:
                        display(username + " disconnected with a LOGOUT message.");
                        keepGoing = false;
                        break;
                    case ChatMessage.WHOISIN:
                        writeMsg("List of the users connected at " + sdf.format(new Date()) + "\n");
                        for(int i = 0; i < clientThreads.size(); ++i) {
                            ClientThread ct = clientThreads.get(i);
                            writeMsg((i+1) + ") " + ct.username + " since " + ct.date);
                        }
                        break;
                }
            }
            // remove myself from the arrayList containing the list of the
            // connected Clients
            remove(id);
            close();
        }

        private boolean writeMsg(String msg) {
            if(!socket.isConnected()){
                close();
                return false;
            }
            try {
                sOutput.writeObject(msg);
            } catch (IOException e) {
                display("Error sending message to " + username);
                display(e.toString());
            }
            return false;
        }

        private void close() {
            try {
                if(sOutput != null) sOutput.close();
            }
            catch(Exception e) {}
            try {
                if(sInput != null) sInput.close();
            }
            catch(Exception e) {};
            try {
                if(socket != null) socket.close();
            }
            catch (Exception e) {}
        }
    }
}
