package com.chat.older.xiechen.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "forward:/error/404.html";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "forward:/error/500.html";
            } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
                return "forward:/error/error.html?code=403";
            } else if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                return "forward:/error/error.html?code=401";
            } else if (statusCode == HttpStatus.BAD_REQUEST.value()) {
                return "forward:/error/error.html?code=400";
            } else if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()) {
                return "forward:/error/error.html?code=408";
            } else if (statusCode == HttpStatus.BAD_GATEWAY.value()) {
                return "forward:/error/error.html?code=502";
            } else if (statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                return "forward:/error/error.html?code=503";
            } else if (statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
                return "forward:/error/error.html?code=504";
            }
        }

        return "forward:/error/error.html";
    }
}
