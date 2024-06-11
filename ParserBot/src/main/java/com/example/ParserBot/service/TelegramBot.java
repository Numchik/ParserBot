package com.example.ParserBot.service;


import com.example.ParserBot.config.BotConfig;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

//
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component

public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    static final String HELP_TEXT = "Бот покажет свежие новости с сайта Дк го Ревда. \n\n " +
            "Команды для пользования ботом: (Описание чтоделает каждая из команд)\n\n" +
            "Команда /start    - (Запускает бота)\n\n" +
            "Команда /news   - (Показывает новости с сайта ДК го Ревда)\n\n";



    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Активирует бота"));
        listOfCommands.add(new BotCommand("/help", "Описание бота и возможно используемые команды "));
        listOfCommands.add(new BotCommand("/news", "Покажет Новости"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.info("Такой комнды нет в боте" + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String massageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (massageText) {
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/help":
                    sendMessage(Long.valueOf(chatId), HELP_TEXT);
                    break;
                case "/news":
                    sendNewsMessage(chatId);
                    break;
                default:
                    sendMessage(Long.valueOf(chatId), "Sorry, command was");
            }

        }

    }

    private void startCommandReceived(long chatId, String name) {
        String answer = " Hi, " + name + ", рад тебя видеть!" +
                " для Знакомство со мной используй команду /help";
        sendMessage(Long.valueOf(chatId), answer);


    }


    private void sendMessage(Long chatId, String textToSet) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSet);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendNewsMessage(long chatId) {
        newsParse(chatId);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));

        for (String el : newsInfo) {
            message.setText(el);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }


        }
        InputFile file = new InputFile();
        File myFile = new File("News.txt");
        file.setMedia(myFile);
        sendDocument(chatId, file);


    }
    //
    private void sendDocument(long chatid, InputFile sendFile) {
        SendDocument document = new SendDocument();
        document.setChatId(String.valueOf(chatid));
        document.setDocument(sendFile);
        document.setCaption("Список новостей! С сайта дворца культуры городского окурга Ревда.");
        try {
            execute(document);
            System.out.println("file is sending");
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    List<String> newsInfo = new ArrayList<>();

    private void newsParse(long chatId) {
        try {
            newsInfo.clear();
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String formattedDate = today.format(formatter);
            System.out.println(formattedDate);
            // Подключаемся к сайту
            Document doc = connectRetry("https://www.dk-revda.ru/blog/categories/дворец-культуры");

            // Находим все элементы
            Elements titleBlock = doc.getElementsByClass("gallery-item-container item-container-regular has-custom-focus visible hover-animation-fade-in");
            // Записываем полученные данные в файл
             writeFile(titleBlock, "News.txt");
            int i = 0;
            // Проходим по всем найденным элементам
            for (Element Block : titleBlock) {
                // Извлекаем текст заголовка новости
                String date = "Дата:" + Block.getElementsByClass("post-metadata__date time-ago").text() + ". \n";
                String title = "Новость: " + Block.getElementsByClass("bD0vt9 KNiaIk").text() + ". \n";
                String link = Block.getElementsByClass("O16KGI pu51Xe TBrkhx xs2MeC has-custom-focus i6wKmL").attr("href");
                String subTitle = "Карткое описание: " + Block.getElementsByClass("BOlnTh").text() + ". \n";
                // Выводим заголовок новости
                newsInfo.add(date + title + subTitle + link );
                i++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Функция для подключения к сайту с возможностью переподключения в случае ошибки
    private static Document connectRetry(String url) throws IOException {
        int maxRetries = 3;
        int retries = 0;
        while (true) {
            try {
                return Jsoup.connect(url).get();
            } catch (IOException e) {
                retries++;
                if (retries == maxRetries) {
                    throw e;
                }
                System.out.println("Ошибка при подключении к сайту. Попытка переподключения...");
            }
        }
    }

    // Функция для записи данных в файл
    private static void writeFile(Elements data, String fileName) {
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            for (Element Block : data) {
                // Извлекаем текст заголовка новости
                String date = "Дата:" + Block.getElementsByClass("post-metadata__date time-ago").text() + ". \n";
                String title = "Новость: " + Block.getElementsByClass("bD0vt9 KNiaIk").text() + ". \n";
                String link = Block.getElementsByClass("O16KGI pu51Xe TBrkhx xs2MeC has-custom-focus i6wKmL").attr("href");
                String subTitle = "Карткое описание: " + Block.getElementsByClass("BOlnTh").text() + ". \n";
                // Выводим заголовок новости
                fileWriter.write(date + title + link + subTitle);
            }
            System.out.println("Данные успешно записаны в файл " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка при записи данных в файл: " + e.getMessage());
        }
    }

}


