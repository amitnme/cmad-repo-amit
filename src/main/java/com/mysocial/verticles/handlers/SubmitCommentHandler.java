package com.mysocial.verticles.handlers;

import static com.mysocial.util.Constants.COOKIE_HEADER;
import static com.mysocial.util.Constants.RESPONSE_HEADER_CONTENT_TYPE;
import static com.mysocial.util.Constants.RESPONSE_HEADER_JSON;

import java.util.Date;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysocial.beans.Comment;
import com.mysocial.beans.User;
import com.mysocial.db.CommentPersistence;
import com.mysocial.util.MySocialUtil;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;

public class SubmitCommentHandler implements Handler<RoutingContext> {
	
	Vertx vertx;
	
	public SubmitCommentHandler(Vertx vertx) {
		this.vertx = vertx;
	}
	
	public void handle(RoutingContext routingContext) {
		
		HttpServerResponse response = routingContext.response();
		response.putHeader(RESPONSE_HEADER_CONTENT_TYPE, RESPONSE_HEADER_JSON);
		String blogId = routingContext.request().getParam(CommentPersistence.KEY_BLOG_ID);
		
		try {

			String commentJsonStr = routingContext.getBodyAsString();
			ObjectMapper commentMapper = new ObjectMapper();
			commentMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			Comment c = commentMapper.readValue(commentJsonStr, Comment.class);
			c.setId(new ObjectId());
			c.setDate(Long.toString(new Date().getTime()));
			c.setBlogId(new ObjectId(blogId));
			System.out.println("Got comment from request");
			
			vertx.executeBlocking(future -> {
				User u = MySocialUtil.getSignedInUser(routingContext);
				if (u != null){
					c.setUserId(u.getId());
					c.setUserFirst(u.getFirst());
					c.setUserLast(u.getLast());
				}
				try {
					CommentPersistence.saveComment(c);
				} catch (Exception ex) {
					System.err.println(ex.getMessage());
					ex.printStackTrace();
					future.complete(null);
				}
				future.complete(c);
			}, resultHandler -> {
				if (resultHandler.succeeded()) {
					Comment savedComment = (Comment) resultHandler.result();
					if (savedComment != null && savedComment.getUserId() != null) {
						response.setStatusCode(HttpResponseStatus.OK.code());
						routingContext.removeCookie(COOKIE_HEADER);
						routingContext.addCookie(Cookie.cookie(COOKIE_HEADER, c.getUserId().toHexString()));
						response.end();
					} else {
						System.err.println("Failed to retrieve saved comment object OR did not find a signed in user");
						response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
						routingContext.removeCookie(COOKIE_HEADER);
						response.end();
					}

				} else {
					response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
					routingContext.removeCookie(COOKIE_HEADER);
					response.end(resultHandler.cause().getMessage());
					MySocialUtil.handleFailure(resultHandler, this.getClass());
				}
			});
			
		}
		catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
			response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code());
			routingContext.removeCookie(COOKIE_HEADER);
			response.end();
		}
	}
}
