package com.auction.server.command;

import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.ClientHandler;

@FunctionalInterface
public interface CommandHandler {

    Response handle(Request req, ClientHandler ctx);
}
