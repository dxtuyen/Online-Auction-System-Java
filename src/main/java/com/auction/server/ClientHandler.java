package com.auction.server;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.entity.User;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.observer.AuctionEventManager;
import com.auction.service.UserManager;
import com.auction.util.AppLogger;
import com.auction.util.JsonHelper;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Phục vụ 1 client kết nối. Đồng thời:
 * <ol>
 *   <li>Đọc/ghi mạng cho 1 connection.</li>
 *   <li>Giữ {@link Session} — state per-connection (userId đang đăng nhập).</li>
 *   <li>Là {@link AuctionObserver} — nhận event realtime khi client {@code WATCH_AUCTION}.</li>
 * </ol>
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private static final Logger log = AppLogger.get(ClientHandler.class);

    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    /** Singleton — toàn server dùng chung 1 router stateless. */
    private final RequestRouter router = RequestRouter.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    /** State đăng nhập của connection này. Mỗi handler ↔ 1 session. */
    private final Session session = new Session();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public Session getSession() { return session; }

    @Override
    public void run() {
        try {
            // UTF-8 bắt buộc để xử lý tiếng Việt đúng.
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {
                // Protocol: mỗi dòng là một JSON request.
                handleLine(line);
            }
        } catch (IOException e) {
            // Client ngắt connection hoặc mạng lỗi — bình thường, không log như error.
        } finally {
            cleanup();
        }
    }

    /**
     * Parse một dòng JSON rồi đẩy xuống router.
     *
     * <p>Lỗi parse KHÔNG đóng connection — server trả {@code ERROR} response cho
     * client biết request đó hỏng, các request sau vẫn xử lý bình thường.</p>
     */
    private void handleLine(String line) {
        try {
            Request req = JsonHelper.parseRequest(line);
            if (req == null || req.getAction() == null) {
                String requestId = req != null ? req.getRequestId() : null;
                send(Response.error("UNKNOWN", "Request không hợp lệ").withRequestId(requestId));
                return;
            }
            Response res = router.dispatch(req, this);
            if (res != null) send(res);
        } catch (Exception e) {
            log.log(Level.WARNING, "Lỗi xử lý request: " + e.getMessage(), e);
            send(Response.error("UNKNOWN", "Lỗi parse: " + e.getMessage()));
        }
    }

    /**
     * Gửi Response xuống client.
     *
     * <p><b>synchronized:</b> nhiều thread có thể cùng ghi vào 1 writer:
     * <ul>
     *   <li>Thread đang xử lý request hiện tại trả response thông thường.</li>
     *   <li>Thread observer (đến từ {@link AuctionEventManager}) push event realtime.</li>
     * </ul>
     * Không đồng bộ thì 2 JSON có thể bị xen lẫn dòng → client parse fail.</p>
     */
    public synchronized void send(Response response) {
        if (writer != null) {
            writer.println(JsonHelper.toJson(response));
        }
    }

    /**
     * Dọn dẹp khi client disconnect:
     * - Gỡ khỏi mọi auction đã subscribe → tránh memory leak (event manager còn giữ ref tới handler chết).
     * - Đóng socket.
     */
    private void cleanup() {
        eventManager.unsubscribeAll(this);
        var addr = socket != null ? socket.getRemoteSocketAddress() : null;
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) { }
        log.info(() -> "Client disconnect: " + addr);
    }

    // ============= AuctionObserver implementation =============
    // Mỗi callback được AuctionEventManager forward khi auction client đang subscribe có thay đổi.
    // Handler chỉ chuyển event domain thành JSON push tương ứng.

    // Mọi UUID đều convert sang String để client parse đồng nhất (tránh Gson default
    // serialize UUID thành object {mostSigBits, leastSigBits} làm vỡ logic so sánh ở client).

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId().toString());
        data.put("bidId", bid.getId().toString());
        data.put("bidderId", bid.getBidderId().toString());
        // Resolve username để client cập nhật lblLeader trực tiếp từ push,
        // không phải đợi loadBidHistory chạy xong (bị async + flicker).
        String bidderName = UserManager.getInstance().findById(bid.getBidderId())
                .map(User::getUsername).orElse("?");
        data.put("bidderName", bidderName);
        data.put("amount", bid.getBidAmount());
        data.put("currentPrice", auction.getCurrentPrice());
        data.put("totalBids", auction.getTotalBids());
        data.put("timestamp", bid.getTimestamp().toString());
        send(Response.push("BID_UPDATE", data));
    }

    @Override
    public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId().toString());
        data.put("oldStatus", oldStatus.name());
        data.put("status", newStatus.name());
        data.put("currentPrice", auction.getCurrentPrice());
        // highestBidderId có thể null (chưa ai bid) — giữ null để client biết.
        data.put("highestBidderId", auction.getHighestBidderId() == null
                ? null : auction.getHighestBidderId().toString());
        send(Response.push("AUCTION_STATUS", data));
    }

    @Override
    public void onAuctionExtended(Auction auction, int extendedSeconds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId().toString());
        data.put("newEndTime", auction.getEndTime().toString());
        data.put("extendedSeconds", extendedSeconds);
        send(Response.push("AUCTION_EXTENDED", data));
    }
}
