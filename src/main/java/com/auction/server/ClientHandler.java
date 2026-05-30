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

public class ClientHandler implements Runnable, AuctionObserver {

    private static final Logger log = AppLogger.get(ClientHandler.class);

    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private final RequestRouter router = RequestRouter.getInstance();
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    private final Session session = new Session();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public Session getSession() { return session; }

    @Override
    public void run() {
        try {

            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {

                handleLine(line);
            }
        } catch (IOException e) {

        } finally {
            cleanup();
        }
    }

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

    public synchronized void send(Response response) {
        if (writer != null) {
            writer.println(JsonHelper.toJson(response));
        }
    }

    private void cleanup() {
        eventManager.unsubscribeAll(this);
        var addr = socket != null ? socket.getRemoteSocketAddress() : null;
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) { }
        log.info(() -> "Client disconnect: " + addr);
    }

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId().toString());
        data.put("bidId", bid.getId().toString());
        data.put("bidderId", bid.getBidderId().toString());

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
