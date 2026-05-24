package com.auction.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NetworkClient {
    private static NetworkClient instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public boolean connect(String host, int port) {
        if (socket != null && !socket.isClosed()) {
            return true;
        }
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Bỏ qua lời chào ban đầu từ Server (CHAO_MUNG|AuctionServer)
            String welcome = in.readLine();
            System.out.println("[CLIENT] Đã kết nối: " + welcome);
            return true;
        } catch (IOException e) {
            System.err.println("[CLIENT] Lỗi kết nối Server: " + e.getMessage());
            return false;
        }
    }

    public String sendRequest(String request) {
        if (socket == null || socket.isClosed()) {
            // Thử kết nối lại
            if (!connect("localhost", 9999)) {
                return "LOI|Khong the ket noi den Server";
            }
        }
        try {
            out.println(request);
            return in.readLine();
        } catch (IOException e) {
            return "LOI|Loi mang: " + e.getMessage();
        }
    }

    public void disconnect() {
        try {
            if (out != null) out.println("QUIT");
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Lỗi đóng kết nối: " + e.getMessage());
        }
    }
    public interface MessageListener {
        void onMessageReceived(String message);
    }

    private MessageListener listener;

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public void startListening() {
        new Thread(() -> {
            try {
                while (true) {
                    String response = in.readLine(); 
                    if (response != null && listener != null) {
                        listener.onMessageReceived(response);
                    }
                }
            } catch (Exception e) {
                System.err.println("Mất kết nối với Server!");
            }
        }).start();
}
}
