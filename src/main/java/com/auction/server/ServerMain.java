package com.auction.server;

import com.auction.bootstrap.DataSeeder;
import com.auction.persistence.Database;
import com.auction.service.AuctionManager;
import com.auction.service.ItemManager;
import com.auction.service.UserManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Entry point của Server.
 *
 * <p>Mở {@link ServerSocket} trên port cố định. Mỗi khi có client kết nối:
 * {@code accept()} trả về 1 {@link Socket} → tạo 1 {@link ClientHandler} Thread
 * riêng để phục vụ client đó. Server chính tiếp tục loop để nhận kết nối mới.</p>
 *
 * <p>Kết quả: server có thể phục vụ nhiều client song song, mỗi client độc lập.</p>
 */
public class ServerMain {

    public static final int PORT = 8888;

    public static void main(String[] args) {
        // 1. Verify DB connection trước khi khởi động — fail fast nếu Docker chưa chạy
        Database.getInstance().verifyConnection();

        // 2. Load cache theo thứ tự dependency: User → Item → Auction
        //    (Auction có FK item_id, seller_id, highest_bidder_id)
        UserManager.getInstance().loadAllFromDb();
        ItemManager.getInstance().loadAllFromDb();
        AuctionManager.getInstance().loadAllFromDb();

        // 3. Seed dữ liệu mẫu (chỉ chạy nếu DB rỗng + SEED_ENABLED=true)
        DataSeeder.run();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Đang lắng nghe trên port " + PORT);
            System.out.println("[Server] Đợi client kết nối...");

            // Loop vô hạn accept kết nối
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Client kết nối: "
                        + clientSocket.getRemoteSocketAddress());

                // Tạo handler thread riêng → server không bị block
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread t = new Thread(handler, "ClientHandler-" + clientSocket.getPort());
                t.setDaemon(false);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi khởi động: " + e.getMessage());
        }
    }
}
