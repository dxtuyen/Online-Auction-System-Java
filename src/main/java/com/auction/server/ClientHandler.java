package com.auction.server;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.observer.AuctionEventManager;
import com.auction.observer.AuctionObserver;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.util.JsonHelper;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phục vụ 1 client kết nối.
 *
 * <p>Implement {@link AuctionObserver} — khi client subscribe vào phiên,
 * có bid mới là ClientHandler tự push JSON xuống client qua socket.</p>
 *
 * <p>Luồng chạy:
 * <ol>
 *   <li>Mở reader/writer UTF-8 từ socket</li>
 *   <li>Loop đọc từng dòng JSON từ client</li>
 *   <li>Parse → gọi {@link RequestRouter} để xử lý</li>
 *   <li>Gửi Response JSON về client</li>
 *   <li>Client ngắt → dọn dẹp observer subscription</li>
 * </ol>
 * </p>
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final RequestRouter router;
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    // id user sau khi login — null nghĩa là chưa đăng nhập
    private String currentUserId;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.router = new RequestRouter(this);
    }

    @Override
    public void run() {
        try {
            // UTF-8 bắt buộc để xử lý tiếng Việt đúng
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            // Client ngắt connection hoặc mạng lỗi
        } finally {
            cleanup();
        }
    }

    /** Parse 1 dòng JSON và đưa cho router xử lý. */
    private void handleLine(String line) {
        try {
            Request req = JsonHelper.parseRequest(line);
            if (req == null || req.getAction() == null) {
                send(Response.error("UNKNOWN", "Request không hợp lệ"));
                return;
            }

            Response res = router.route(req);
            if (res != null) send(res);

        } catch (Exception e) {
            send(Response.error("UNKNOWN", "Lỗi parse: " + e.getMessage()));
        }
    }

    /**
     * Gửi Response xuống client. Dùng synchronized tránh 2 thread ghi vào
     * cùng 1 writer gây lỗi interleaving.
     */
    public synchronized void send(Response response) {
        if (writer != null) {
            writer.println(JsonHelper.toJson(response));
        }
    }

    /** Dọn dẹp khi client disconnect. */
    private void cleanup() {
        // Gỡ khỏi TẤT cả observer subscription để tránh memory leak
        eventManager.unsubscribeAll(this);

        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}

        System.out.println("[Server] Client disconnect: "
                + socket.getRemoteSocketAddress());
    }

    // ============= Accessors =============

    public String getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(String currentUserId) { this.currentUserId = currentUserId; }

    // ============= AuctionObserver implementation =============
    // Mỗi khi có bid mới trên phiên mà client này đang xem → push xuống

    @Override
    public void onNewBid(Auction auction, BidTransaction bid) {
        // Dùng LinkedHashMap để giữ thứ tự field JSON đẹp
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("bidId", bid.getId());
        data.put("bidderId", bid.getBidderId());
        data.put("amount", bid.getBidAmount());
        data.put("currentPrice", auction.getCurrentPrice());
        data.put("totalBids", auction.getTotalBids());
        data.put("timestamp", bid.getTimestamp().toString());

        send(Response.push("BID_UPDATE", data));
    }

    @Override
    public void onAuctionStatusChanged(Auction auction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("status", auction.getStatus().name());
        data.put("currentPrice", auction.getCurrentPrice());
        data.put("highestBidderId", auction.getHighestBidderID());

        send(Response.push("AUCTION_STATUS", data));
    }

    @Override
    public void onAuctionExtended(Auction auction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("newEndTime", auction.getEndTime().toString());

        send(Response.push("AUCTION_EXTENDED", data));
    }
}
