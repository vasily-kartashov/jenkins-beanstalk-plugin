package com.kartashov.jenkins.beanstalk;

import org.apache.commons.lang.StringUtils;

import java.io.PrintStream;

public class LoggerFacade {

    final private static String SINGLE_BAR = "------------------------------------------------------------------------";
    final private static String TOP_BAR    = "╔════════════════════════════════════════╗";
    final private static String BOTTOM_BAR = "╚════════════════════════════════════════╝";
    final private static String VERTICAL_BAR = "║";

    private PrintStream out;

    public LoggerFacade(PrintStream out) {
        this.out = out;
    }

    public LoggerFacade bar() {
        info(SINGLE_BAR);
        return this;
    }

    public LoggerFacade box(String message) {
        info(TOP_BAR);
        info(VERTICAL_BAR + " " + StringUtils.rightPad(message, TOP_BAR.length() - 3) + VERTICAL_BAR);
        info(BOTTOM_BAR);
        return this;
    }

    public LoggerFacade info(String message) {
        out.print("[INFO] ");
        out.println(message);
        return this;
    }

    public LoggerFacade warning(String message) {
        out.print("[WARNING] ");
        out.println(message);
        return this;
    }

    public LoggerFacade error(String message) {
        out.print("[ERROR] ");
        out.println(message);
        return this;
    }
}
