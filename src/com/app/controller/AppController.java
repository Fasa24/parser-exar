package com.app.controller;

import com.app.model.ParseResult;
import com.app.model.ParserEngine;
import com.app.view.MainView;

import java.util.List;

public class AppController {
    private final MainView view;
    private final ParserEngine model;

    public AppController(MainView view, ParserEngine model) {
        this.view = view;
        this.model = model;

        this.view.setParseButtonListener(_ -> executeParse());
    }

    private void executeParse() {
        view.clearResults();

        String text = view.getInputText();

        if (text == null || text.trim().isEmpty()) {
            view.displayWarning("The input text is empty.", "Warning");
            return;
        }

        List<ParseResult> results = model.parseInput(text);

        for (ParseResult item : results) {
            view.addResultRow(new Object[] {
                    item.id(),
                    item.lineNumber(),
                    item.expression(),
                    item.code(),
                    item.message()
            });
        }
    }
}