//Mavenで使うもの
package com.example;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

//javaで使うもの
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.awt.Font;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;

//個人を区別するためのクラス
class Person
{
    String grade;
    String name;

    Map<String,List<String>>Times = new LinkedHashMap<>();
}

//端末ごとに別ページに飛ばすためのクラス
class Session
{
    //担当者の保存場所設定
    Map<String,Map<String,List<String>>> assignedShift = new LinkedHashMap<>();
    //人数上限・回数上限の設定
    int slotLimit = 2;
    int dailyLimit = 2;
    //ボタン・表の時間帯の順番を固定
    List<String> allTimes = new ArrayList<>();
    List<String> allDates = new ArrayList<>();
    //シフト表を生成
    Map<String,Map<String,List<String>>> currentShiftTable = new LinkedHashMap<>();
}

public class makeShift
{
    static Map<String,Session> sessions = new LinkedHashMap<>();
    //複数選択を可読にするためのメソッド
    public static List<String> parseCsvLine(String line)
    {
        List<String> result = new ArrayList<>();

        StringBuilder cell = new StringBuilder();

        boolean inQuotes = false;
        for(int i = 0 ; i < line.length(); i++)
        {
            char c = line.charAt(i);
            if(c == '"'){inQuotes = !inQuotes;}
            else if(c == ',' && !inQuotes)
            {
                result.add(cell.toString());
                cell.setLength(0);
            }
            else{cell.append(c);}
        }
        result.add(cell.toString());
        return result;
    }

    public static Map<String,String> parseFormData(String data)
    {
        Map<String,String> result = new LinkedHashMap<>();
        String[] pairs = data.split("&");
        for(String pair : pairs)
        {
            String[] parts = pair.split("=",2);
            if(parts.length == 2)
            {
                String key = parts[0];
                String value = URLDecoder.decode(parts[1],StandardCharsets.UTF_8);
                result.put(key,value);
            }
        }
        return result;
    }

    //URLを取得し，スプレッドシートをCSV形式で取得するメソッド
    public static String[] toCSV(HttpExchange exchange,Session session) throws IOException
    {
        InputStream is = exchange.getRequestBody();
                String data = new String(is.readAllBytes(),StandardCharsets.UTF_8);

                Map<String,String> formData = parseFormData(data);
                String sheetUrl = formData.get("sheetUrl");
                session.slotLimit = Integer.parseInt(formData.get("slotLimit"));
                session.dailyLimit = Integer.parseInt(formData.get("dailyLimit"));
                
                String[] firstSplit = sheetUrl.split("/d/");
                if(firstSplit.length<2){throw new IllegalArgumentException("適切なURLを入力してください。");}
                String afterD = firstSplit[1];
                String[] secondSplit = afterD.split("/");
                String sheetId = secondSplit[0];

                //CSVファイルを取得
                String csvUrl = "https://docs.google.com/spreadsheets/d/" + sheetId + "/export?format=csv";
                //URLを作成(URIの経由が必要)
                URL url = URI.create(csvUrl).toURL();
                //csvを取得するためのReader作成
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(),StandardCharsets.UTF_8));
                //CSVとして1行ずつ格納
                StringBuilder csvData = new StringBuilder();
                String line;
                while((line = reader.readLine())!= null)
                {
                    csvData.append(line);
                    csvData.append("\n");
                }
                reader.close();

                return csvData.toString().split("\n");
    }

    //個人の回答を解析するメソッド
    public static List<Person> getPerson(String[] rows,List<String> headerColumns)
    {
        List<Person> persons = new ArrayList<>();
        for(int i = 1; i < rows.length; i++)
        {
            String row = rows[i];
            List<String> columns = parseCsvLine(row);
            Person person = new Person();

            if(columns.size() < 3){continue;}

            person.grade = columns.get(1);
            person.name = columns.get(2);

            //日付ごとに回答を読み込む
            for(int j = 3; j < columns.size(); j++)
            {
                String date = headerColumns.get(j);
                String answer = columns.get(j);

                //新たにtimesリストを追加
                List<String> times = new ArrayList<>();
                if(!answer.isEmpty())
                {
                    //回答が空欄でないなら,カンマごとに区分する
                    for(String t : answer.split(","))
                    {
                        //空白を除去し，timesリストに追加
                        String time = t.trim();
                        //「なし」のみ回答から除外
                        if(!time.equals("なし")){times.add(time);}                            }
                }
                person.Times.put(date,times);
            }
            persons.add(person);
        }      
        return persons;
    }

    //全時間帯を取得し，追い出しを左右に分けるメソッド
    public static void buildAllTimes(List<Person> persons,List<String>headerColumns,Session session)
    {
        for(int i =3; i < headerColumns.size(); i++)
        {
            String date = headerColumns.get(i);
            for(Person person : persons)
            {
                List<String> times =person.Times.get(date);
                if(times == null){continue;}

                for(String time : times)
                {
                    if("追い出し".equals(time))
                    {
                        if(!session.allTimes.contains("追い出し(左)")){session.allTimes.add("追い出し(左)");}
                        if(!session.allTimes.contains("追い出し(右)")){session.allTimes.add("追い出し(右)");}
                    }
                    else
                    {
                        if(!session.allTimes.contains(time)){session.allTimes.add(time);}
                    }
                }
            }   
        }
    }

    //全員のデータから候補者一覧を生成するメソッド
    public static Map<String,Map<String,List<String>>> buildShiftTable(List<Person> persons,Session session)
    {
        Map<String,Map<String,List<String>>> shiftTable = new LinkedHashMap<>();
        for(Person person : persons)
        {
            //日付ごとに処理
            for(String date : person.Times.keySet())
            {
                //各時間帯を取得，日付がないなら専用のMap生成
                List<String> times = person.Times.get(date);
                if(!shiftTable.containsKey(date))
                {
                    shiftTable.put(date,new LinkedHashMap<>());
                }

                //その日の時間帯を取得
                Map<String,List<String>> dateMap = shiftTable.get(date);
                for(String time : times)
                {
                    if(time.equals("追い出し"))
                    {
                        if(!dateMap.containsKey("追い出し(左)"))
                        {
                            dateMap.put("追い出し(左)", new ArrayList<>());
                        }

                        if(!dateMap.containsKey("追い出し(右)"))
                        {
                            dateMap.put("追い出し(右)", new ArrayList<>());
                        }

                        dateMap.get("追い出し(左)").add(person.name);
                        dateMap.get("追い出し(右)").add(person.name);
                    }
                    else
                    {
                        if(!dateMap.containsKey(time))
                        {
                            dateMap.put(time, new ArrayList<>());
                        }

                        dateMap.get(time).add(person.name);
                    }
                }
            }
        }
        //日付一覧を保存する
        session.allDates.clear();
        for(String date : shiftTable.keySet()){session.allDates.add(date);}

        //シフト表を保存
        session.currentShiftTable.clear();
        for(String date : shiftTable.keySet())
        {
            Map<String,List<String>> copiedDateMap = new LinkedHashMap<>();
            for(String time : shiftTable.get(date).keySet())
            {
                copiedDateMap.put(time,new ArrayList<>(shiftTable.get(date).get(time)));
            }
            session.currentShiftTable.put(date,copiedDateMap);
        }
        return shiftTable;
    }

    //日付タブボタン用メソッド
    public static StringBuilder buildDateTabs(Map<String,Map<String,List<String>>> shiftTable, String targetedTab, String target, boolean previewLayout)
    {
        StringBuilder dateTabs = new StringBuilder();
        
        if(previewLayout){dateTabs.append("<div class='previewDateTabs'>");}
        else{dateTabs.append("<div>");}
        
        boolean firstButton = true;
                
        for(String date : shiftTable.keySet())
        {
            String tabId = target + "Tab_" + date.replaceAll("[^a-zA-Z0-9]","_");
            String buttonId = "btn_" + tabId;
            String buttonClass = target + "TabButton";
            String buttonStyle;
            String selected = firstButton ? "true" : "false";
            
            buttonStyle =
                "padding:8px 16px;" +
                "border:1px solid #888;" +
                "cursor:pointer;";
           
            dateTabs.append
            (
                "<button type='button'" + "class='" + buttonClass + "'" + 
                "id='" + buttonId + "' " + 
                "data-selected='" + selected + "'" +
                "onclick=\"" + targetedTab + "('" + tabId + "','" + buttonId + "')\" " +
                "style='" + buttonStyle + "'>" + date + 
                "</button>"
            );
            firstButton = false;
        }
        dateTabs.append("</div>");
        return dateTabs;
    }

    //elsx,pngを出力するためのボタン用メソッド
    public static StringBuilder buildExportButton()
    {
        StringBuilder exportButton = new StringBuilder();
        exportButton.append(
            "<div style='display:flex; gap:10px; margin-bottom:15px;'>" +

            "<form method='POST' action='/export'>" +
            "<input type='submit' value='Excel出力'>" +
            "</form>" +

            "<form method='POST' action='/exportImage'>" +
            "<input type='submit' value='画像として保存'>" +
            "</form>" +

            "</div>"
        );
        return exportButton;
    }

    //Sessionを取得する
    public static Session getSession(String sessionId)
    {
        Session session = sessions.get(sessionId);
        if(session == null)
        {
            session = new Session();
            sessions.put(sessionId,session);
        }
        return session;
    }
    //CookieからSessionを取得するためのメソッド
    public static Session getsession(HttpExchange exchange)
    {
        String sessionId = null;

        List<String> cookies = exchange.getRequestHeaders().get("Cookie");

        if(cookies != null)
        {
            for(String cookieLine : cookies)
            {
                String[] cookiePairs = cookieLine.split(";");
                for(String pair : cookiePairs)
                {
                    pair = pair.trim();
                    if(pair.startsWith("SESSION_ID="))
                    {
                        sessionId = pair.substring("SESSION_ID=".length());
                    }
                }
            }
        }

        if(sessionId == null)
        {
            sessionId = Long.toHexString(System.currentTimeMillis()) + Long.toHexString((long)(Math.random()*1000000));

            exchange.getResponseHeaders().add("Set-Cookie" ,"SESSION_ID=" + sessionId + "; Path=/");
        }
        return getSession(sessionId);
    }

    //html呼び出し用メソッド
    private static String loadFile(String fileName) throws IOException
    {
        try(InputStream is = makeShift.class.getResourceAsStream("/" + fileName))
        {
            if(is == null){throw new FileNotFoundException(fileName);}
            byte[] bytes = is.readAllBytes();

            return new String(bytes,StandardCharsets.UTF_8);
        }
    }

    //シフト表のレイアウト用のクラス
    static class LayoutInfo
    {
        Session session;
        LayoutInfo(Session session){this.session = session;}
        
        int horizontalOffset = 1;
        int verticalOffset = 2;
        int timeWidth = 140;
        int personWidth = 110;
        int rowHeight = 28;
        int tableWidth(){return session.slotLimit+1;}
        int tableHeight(){return session.allTimes.size()+1;}
        int startCol(int d)
        {
            int columnBlock = (d<3)?0:1;
            return columnBlock*(session.slotLimit+2) + horizontalOffset;
        }
        int startRow(int d)
        {
            int rowBlock = (d<3)? d:d-3;
            return rowBlock*(tableHeight()+1) + verticalOffset;
        }
        int x(int d)
        {
            int columnBlock = (d<3)?0:1;
            int tablePixcelWidth = timeWidth + session.slotLimit*personWidth;
            return columnBlock*(tablePixcelWidth+80);
        }
        int y(int d)
        {
            return (startRow(d) - verticalOffset)*rowHeight;
        }
    }
    public static void main(String[]args) throws IOException
    {
        //サーバーを立てる
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port),0);

        //JavaScriptを扱うためのコンテキスト
        server.createContext("/JavaScript/submit.js",(exchange) ->
        {
            ///
            System.out.println(makeShift.class.getResource("/JavaScript/submit.js"));

            InputStream is = makeShift.class.getResourceAsStream("/JavaScript/submit.js");
            
            ///
            if(is == null)
            {
                System.out.println("見つからない");
                exchange.sendResponseHeaders(404, -1);
            }

            byte[] bytes = is.readAllBytes();

            exchange.getResponseHeaders().set("Content-Type","application/javascript; charset=UTF-8");
            exchange.sendResponseHeaders(200,bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        });

        //CSSを扱うためのコンテキスト
        server.createContext("/style.css",(HttpExchange exchange) ->
        {
            InputStream is = makeShift.class.getResourceAsStream("/style.css");
            byte[] css = is.readAllBytes();
            is.close();

            exchange.getResponseHeaders().set("Content-Type","text/css; charset=UTF-8");
            exchange.sendResponseHeaders(200,css.length);

            OutputStream os = exchange.getResponseBody();
            os.write(css);
            os.close();
        });

        //URL入力前のブラウザ画面の構成
        server.createContext("/",(HttpExchange exchange) ->
        {
            String response = loadFile("html/home.html");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");            
            exchange.sendResponseHeaders(200,bytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        });

        //URL入力後のブラウザ画面の構成
        server.createContext("/submit",(HttpExchange exchange) ->
            {
            
                Session session = getsession(exchange);
                //toCSVメソッドを呼び出す
                String[] rows = toCSV(exchange,session);
                //ヘッダーを取り出す
                if(rows.length == 0){ throw new RuntimeException("データが存在しません。");}
                String header = rows[0];
                //ヘッダーを解析
                List<String> headerColumns = parseCsvLine(header);
                //表示順を調整する
                session.allTimes.clear();
                //getPersonメソッドを呼び出す
                List<Person>persons = getPerson(rows,headerColumns);
                //buildAllTimesメソッドを呼び出す
                buildAllTimes(persons,headerColumns,session);
                //buildShiftTableメソッドを呼び出す
                Map<String,Map<String,List<String>>> shiftTable = buildShiftTable(persons,session);

                //日別にボタンとプレビューを扱う用
                Map<String,StringBuilder> shiftResultMap = new LinkedHashMap<>();
                
                //表生成の確認用
                for(String date : shiftTable.keySet())
                {
                    StringBuilder shiftResult = new StringBuilder();

                    shiftResult.append("<h3>");
                    shiftResult.append("</h3>");

                    Map<String,List<String>> dateMap = shiftTable.get(date);

                    //ボタンの動作に関するコード
                    for(String time : session.allTimes)
                    {
                        shiftResult.append("<b>" + time + "</b><br>");
                        //nullエラー対策
                        List<String> candidates = dateMap.get(time);
                        //担当者の表示
                        List<String> assigned = new ArrayList<>();
                        if(session.assignedShift.containsKey(date))
                        {
                            Map<String,List<String>> assignedDate = session.assignedShift.get(date);
                            if(assignedDate.containsKey(time)){assigned = assignedDate.get(time);}
                        }

                        if(assigned != null && !assigned.isEmpty())
                        {
                            for(String name : assigned){shiftResult.append(name + " ");}
                            shiftResult.append("<br></br>");
                        }

                        if(candidates == null || candidates.isEmpty())
                        {
                            shiftResult.append("この時間帯に稼働可能な人がいません。");
                            shiftResult.append("<br>");
                            shiftResultMap.put(date,shiftResult);
                        }
                        else
                        {
                            for(String personName : candidates)
                            {
                                String grade = "";
                                for(Person p : persons)
                                {
                                    if(p.name.equals(personName))
                                    {
                                        grade = p.grade;
                                        break;
                                    }
                                }
                                


                                
                                boolean selected = assigned != null && assigned.contains(personName);
                                shiftResult.append(
                                    "<button type='button'" + 
                                    "data-date='" + date + "'" +
                                    "data-time='" + time + "'" +
                                    "data-person='" + personName + "'" +
                                    "data-selected='" + selected + "'" +
                                    "data-disabled='false'" +
                                    ">" + personName + "(" + grade + ")" +
                                    "</button>" );

                            }
                            shiftResult.append("<br><br>");
                        }
                        
                    }
                    shiftResult.append("<br>");
                    shiftResultMap.put(date,shiftResult);
                }
                StringBuilder allShiftTables = new StringBuilder();
                allShiftTables.append("<div>");

                //buildExportButtonメソッド呼び出し
                allShiftTables.append(buildExportButton());

                //タブボタン用メソッド呼び出し
                allShiftTables.append(buildDateTabs(shiftTable,"changeAssignTab","assign",false));

                //担当者選択側のタブボタンとその中身
                boolean firstAssignTab = true;
                for(String date : shiftTable.keySet())
                {
                    String display = firstAssignTab ? "block" : "none" ;
                    String tabId = "assignTab_" + date.replaceAll("[^a-zA-Z0-9]","_");
                    allShiftTables.append(
                        "<div id='" +
                        tabId +
                        "' class='assignTabContent' style='display:" +
                        display +
                        ";" +
                        "border:2px solid #888;" +
                        "padding:15px;" +
                        "margin-top:-1px;" +
                        "background: #fafafa;'>"
                    );

                    allShiftTables.append(
                        "<div style='border:1px solid #ccc;" +
                        "padding:10px;" +
                        "margin-bottom:15px;'>"
                    );
                    allShiftTables.append("<h4>担当者を選択してください。</h4>");
                    allShiftTables.append(shiftResultMap.get(date));
                    allShiftTables.append("</div>");

                    allShiftTables.append("<div style='border:1px solid:#ccc;" + "padding:10px;'>");
                    allShiftTables.append("</div>");

                    allShiftTables.append("</div>");

                    firstAssignTab = false;
                }

                StringBuilder previewArea = new StringBuilder();
                //preview側の日付タブとその中身
                previewArea.append(buildDateTabs(shiftTable, "changePreviewTab","preview",true));
                boolean firstPreviewTab = true;
                for(String date : shiftTable.keySet())
                {
                    String display = firstPreviewTab ? "block" : "none";
                    String tabId = "previewTab_" + date.replaceAll("[^a-zA-Z0-9]","_");

                    previewArea.append(
                        "<div id='" + tabId + "'" +
                        " class='previewTabContent'" +
                        " style='display:" + display + ";'>"
                    );
                    //プレビュー生成
                    previewArea.append("<table>");
                    previewArea.append("<tr>");
                    previewArea.append("<th>");
                    previewArea.append(date);
                    previewArea.append("</th>");

                    for(int i=0; i<session.slotLimit; i++)
                    {
                        previewArea.append("<th></th>");
                    }
                    previewArea.append("</tr>");

                    for(String time : session.allTimes)
                    {
                        previewArea.append("<tr>");
                        previewArea.append("<td>");
                        previewArea.append(time);
                        previewArea.append("</td>");

                        List<String> assignedPersons = new ArrayList<>();

                        if(session.assignedShift.containsKey(date))
                        {
                            Map<String,List<String>> assignedDate = session.assignedShift.get(date);
                            if(assignedDate.containsKey(time)){assignedPersons = assignedDate.get(time);}
                        }
                        for(int i=0; i<session.slotLimit; i++)
                        {
                            String cellId = "preview_" +
                                date.replaceAll("[^a-zA-Z0-9]","_") + "_" +
                                time.replaceAll("[^a-zA-Z0-9]","_") + "_" + i; 
                            previewArea.append("<td id='" + cellId + "'>");
                            if(i<assignedPersons.size()){previewArea.append(assignedPersons.get(i));}
                            previewArea.append("</td>");
                        }
                        previewArea.append("</tr>");
                    }
                    previewArea.append("</table>");
                    previewArea.append("</div>");
                    firstPreviewTab = false;
                }
                //スライドボタン生成
                previewArea.append(
                    "<div class='tabSlideButton' style='padding:10px; border-bottom:1px solid #ccc;'>" +
                    "<label>" +
                    "<input type='checkbox' id='uniformTabs' checked>" +
                    " 日付タブの選択状況を統一する" +
                    "</label>" +
                    "</div>"
                );                

                //ブラウザ側への返答用HTML
                String response = loadFile("html/submit.html");

                response = response.replace("{{SLOT_LIMIT}}",String.valueOf(session.slotLimit));
                response = response.replace("{{DAILY_LIMIT}}",String.valueOf(session.dailyLimit));
                response = response.replace("{{ALL_SHIFTTABLES}}",allShiftTables.toString());
                response = response.replace("{{PREVIEW_TABLE}}",previewArea.toString());

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200,response.getBytes().length);
            
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        );

        server.createContext("/assign",(HttpExchange exchange) ->
        {
            Session session = getsession(exchange);
  
            InputStream is = exchange.getRequestBody();

            String data = new String(is.readAllBytes(),StandardCharsets.UTF_8);

            Map<String,String> formData = parseFormData(data);
            
            String date = formData.get("date");
            String time = formData.get("time");
            String person = formData.get("person");

            if(!session.assignedShift.containsKey(date)){session.assignedShift.put(date,new LinkedHashMap<>());}
            if(!session.assignedShift.get(date).containsKey(time)){session.assignedShift.get(date).put(time,new ArrayList<>());}
            
            
            List<String> assignedList = session.assignedShift.get(date).get(time);
            
            //その日の最大担当回数を記録
            int assignedCount = 0;
            if(session.assignedShift.containsKey(date))
            {
                Map<String,List<String>> assignedDate = session.assignedShift.get(date);

                for(List<String> persons : assignedDate.values())
                {if(persons.contains(person)){assignedCount++;}}
            }

            //登録済みなら解除する
            if(assignedList.contains(person)){assignedList.remove(person);}
            //未登録なら登録する
            else if((assignedList.size() < session.slotLimit) && (assignedCount < session.dailyLimit)){assignedList.add(person);}

            assignedCount = 0;
            if(session.assignedShift.containsKey(date))
            {
                Map<String,List<String>> assignedDate = session.assignedShift.get(date);
                for(List<String> persons : assignedDate.values())
                {
                    if(persons.contains(person)){assignedCount++;}
                }
            }
            boolean selected = assignedList.contains(person);

            /* 
            //disabledを管理するJson
            StringBuilder disabledJson = new StringBuilder();
            disabledJson.append("[");

            boolean firstDisabled = true;

            if(assignedList.size() >= session.slotLimit)
            {
                disabledJson.append("{");
                disabledJson.append("\"type\":\"slotLimit\",");
                disabledJson.append("\"date\":\"").append(date).append("\",");
                disabledJson.append("\"time\":\"").append(time).append("\"");
                disabledJson.append("}");

                firstDisabled = false;
            }

            if(assignedCount >= session.dailyLimit)
            {
                if(!firstDisabled){disabledJson.append(",");}

                disabledJson.append("{");
                disabledJson.append("\"type\":\"dailyLimit\",");
                disabledJson.append("\"date\":\"").append(date).append("\",");
                disabledJson.append("\"person\":\"").append(person).append("\"");
                disabledJson.append("}");

                firstDisabled = false;
            }

            if(time.equals("追い出し(左)") || time.equals("追い出し(右)"))
            {
                if(!firstDisabled){disabledJson.append(",");}

                String otherTime;
                if(time.equals("追い出し(左)")){otherTime = "追い出し(右)";}
                else{otherTime = "追い出し(左)";}

                disabledJson.append("{");
                disabledJson.append("\"type\":\"selectedOtherOidashi\",");
                disabledJson.append("\"date\":\"").append(date).append("\",");
                disabledJson.append("\"time\":\"").append(otherTime).append("\",");
                disabledJson.append("\"person\":\"").append(person).append("\"");
                disabledJson.append("}");

                firstDisabled = false;
            }
            disabledJson.append("]");
            */

            StringBuilder personsJson = new StringBuilder();
            personsJson.append("[");
            for(int i=0; i<assignedList.size(); i++)
            {
                if(i>0){personsJson.append(",");}
                personsJson.append("\"");
                personsJson.append(assignedList.get(i));
                personsJson.append("\"");
            }
            personsJson.append("]");

            String response=
            "{" +
                "\"selected\":" + selected + "," +
                "\"count\":" + assignedList.size() + "," +
                "\"date\":\"" + date + "\"," +
                "\"time\":\"" + time + "\"," +
                "\"persons\":" + personsJson.toString() + /*"," +
                "\"disabledJson\":" + disabledJson.toString() + */
            "}";

            exchange.getResponseHeaders().set("Content-type","application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200,response.getBytes(StandardCharsets.UTF_8).length);

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes(StandardCharsets.UTF_8));            
            os.close();
        });

        //シフト表をExcelで出力する用
        server.createContext("/export",(HttpExchange exchange) ->
        {
            //エラー確認用
            try
            {
                Session session = getsession(exchange);
                //レイアウト用クラス呼び出し
                LayoutInfo layout = new LayoutInfo(session);
                System.out.println("=== EXPORT START ===");
                System.out.println("Workbook作成前");

                Workbook workbook = new XSSFWorkbook();
                System.out.println("Workbook作成後");

                Sheet sheet = workbook.createSheet("シフト表");
           
                //背景色を白にするための下準備
                CellStyle whiteStyle = workbook.createCellStyle();
                whiteStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
                whiteStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                whiteStyle.setAlignment(HorizontalAlignment.CENTER);
                //枠線をつけるための下準備
                CellStyle borderStyle = workbook.createCellStyle();
                borderStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
                borderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);  
                borderStyle.setBorderTop(BorderStyle.THIN);
                borderStyle.setBorderBottom(BorderStyle.THIN);
                borderStyle.setBorderLeft(BorderStyle.THIN);
                borderStyle.setBorderRight(BorderStyle.THIN);
                borderStyle.setAlignment((HorizontalAlignment.CENTER));
                //日付セルを着色するための下準備
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
                dateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                dateStyle.setAlignment(HorizontalAlignment.CENTER);

                //表の配置設定
                int horizontalOffset = 1;
                int verticalOffset = 2;
                int maxTableRows = session.allTimes.size()+1;
                int margin = 1;
                int firstRow = verticalOffset - margin;
                int firstCol = horizontalOffset - margin;
                int lastRow = verticalOffset + 2*(maxTableRows+1) + maxTableRows-1 + margin;
                int lastCol = horizontalOffset + (session.slotLimit+2) + session.slotLimit + margin;

                //背景色を白にする
                for(int r = firstRow; r<=lastRow; r++)
                {
                    Row row = sheet.getRow(r);
                    if(row == null){row = sheet.createRow(r);}
                
                    for(int c = firstCol; c<=lastCol; c++)
                    {
                        Cell cell = row.getCell(c);
                        if(cell == null){cell = row.createCell(c);}
                    
                        cell.setCellStyle(whiteStyle);
                    }
                }

                //シフト表を生成
                List<String> dates = new ArrayList<>(session.currentShiftTable.keySet());
                for(int d = 0; d<dates.size(); d++)
                {
                    String date = dates.get(d);

                    //左右の列感覚の調整
                    int startCol = layout.startCol(d);
                    int startRow = layout.startRow(d);

                    Map<String,List<String>> assignedDateMap = session.assignedShift.get(date);
                    if(assignedDateMap == null){assignedDateMap = new LinkedHashMap<>();}

                    Row dateRow = sheet.getRow(startRow);
                    if(dateRow == null){dateRow = sheet.createRow(startRow);}
                    Cell dateCell = dateRow.createCell(startCol);
                    dateCell.setCellValue(date);

                    int currentRow = startRow+1;
                    for(String time : session.allTimes)
                    {
                        Row row = sheet.getRow(currentRow);
                        if(row == null){row = sheet.createRow(currentRow);}

                        Cell timeCell = row.createCell(startCol);
                        timeCell.setCellValue(time);

                        List<String> persons = assignedDateMap.get(time);
                        for(int i=0; i<session.slotLimit; i++)
                        {
                            Cell personCell = row.createCell(startCol+i+1);
                            if(persons != null && i < persons.size())
                            {
                                personCell.setCellValue(persons.get(i));
                            }
                            else
                            {
                                personCell.setCellValue("");    
                            }
                        }
                        currentRow++;
                    }
                }
            
            

                //シフト表に枠線をつける
                for(int d = 0; d<dates.size(); d++)
                {
                    int columnBlock;
                    int rowBlock;

                    if(d<3)
                    {
                        columnBlock = 0;
                        rowBlock = d;
                    }
                    else
                    {
                        columnBlock = 1;
                        rowBlock = d-3;
                    }

                    int startCol = columnBlock*(session.slotLimit+2)+horizontalOffset;
                    int startRow = rowBlock*(maxTableRows+1)+verticalOffset;

                    for(int r = startRow+1; r<=startRow+session.allTimes.size(); r++)
                    {
                        Row row = sheet.getRow(r);
                        for(int c = startCol; c<=startCol+session.slotLimit; c++)
                        {
                            Cell cell = row.getCell(c);
                            if(cell == null){cell = row.createCell(c);}
                            cell.setCellStyle(borderStyle);
                        }
                    }
                }

                //日付セルを水色に着色
                for(int d = 0; d<dates.size(); d++)
                {
                    int columnBlock;
                    int rowBlock;

                    if(d<3)
                    {
                        columnBlock = 0;
                        rowBlock = d;
                    }
                    else
                    {
                        columnBlock = 1;
                        rowBlock = d-3;
                    }

                    int startCol = columnBlock*(session.slotLimit+2)+horizontalOffset;
                    int startRow = rowBlock*(maxTableRows+1)+verticalOffset;

                    Row row = sheet.getRow(startRow);
                    if(row == null){row = sheet.createRow(startRow);}

                    Cell dateCell = row.getCell(startCol);
                    if(dateCell == null){dateCell = row.createCell(startCol);}
                    dateCell.setCellStyle(dateStyle);
                }

                //列幅自動調整用のおまじない
                int centerGapCol = horizontalOffset + session.slotLimit +1;
                for(int col = firstCol+1; col<=lastCol-1; col++)
                {
                    if(col == centerGapCol){continue;}
                    sheet.autoSizeColumn(col);
                    int width = sheet.getColumnWidth(col);
                    sheet.setColumnWidth(col,Math.min(width+1000,255*256));
                }

                //作成されるファイル名設定
                String fileName = "shift.xlsx";
                if(!dates.isEmpty())
                {
                    String firstDate = dates.get(0);
                    String lastDate = dates.get(dates.size()-1);
                    String[] firstParts = firstDate.split("/");
                    String[] lastParts = lastDate.split("/");
                    String firstName = null;
                    String lastName = null;
                    if(firstParts.length == 2)
                    {
                        int month = Integer.parseInt(firstParts[0]);
                        int day = Integer.parseInt(firstParts[1].replaceAll("\\(.*\\)",""));

                        firstName = String.format("%02d%02d",month,day);
                    }
                    if(lastParts.length == 2)
                    {
                        int month = Integer.parseInt(lastParts[0]);
                        int day = Integer.parseInt(lastParts[1].replaceAll("\\(.*\\)",""));
                    
                        lastName = String.format("%02d%02d",month,day);
                    }
                    fileName ="shift_" + firstName + "~" + lastName + ".xlsx";
                }

                //作成したExcelを書き込む
                System.out.println("Excel書き込み開始");
                ByteArrayOutputStream boas = new ByteArrayOutputStream();
                workbook.write(boas);
                byte[] excelBytes = boas.toByteArray();
                System.out.println("Excel書き込み完了");

                //HTTPのヘッダー設定のためのおまじない
                exchange.getResponseHeaders().set("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=" + fileName);             

                //ファイルをブラウザへ送信
                exchange.sendResponseHeaders(200,excelBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(excelBytes);
                os.close();

                workbook.close();
            }
            catch(Exception e)
            {
                System.out.println("EXPORT ERROR");
                e.printStackTrace();
            }  
        });
        
        //画像として出力するとき用
        server.createContext("/exportImage",(HttpExchange exchange) -> 
        {
            try
            {
                Session session = getsession(exchange);
                LayoutInfo layout = new LayoutInfo(session);
                List<String> dates = new ArrayList<>(session.currentShiftTable.keySet());
                int leftMargin =20;
                int topMargin =20;
                int maxX = 0;
                int maxY = 0;
                for(int d=0; d<dates.size(); d++)
                {
                    int x = leftMargin + layout.x(d);
                    int y = topMargin + layout.y(d);
                    int width = layout.timeWidth + session.slotLimit*layout.personWidth;
                    int height = (session.allTimes.size()+1)*layout.rowHeight;

                    maxX = Math.max(maxX,x+width);
                    maxY = Math.max(maxY,y+height);
                }
                
                int imageWidth = maxX + 20;
                int imageHeight = maxY + 20;

                BufferedImage image = new BufferedImage(imageWidth,imageHeight,BufferedImage.TYPE_INT_RGB);                
                
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0,0,imageWidth,imageHeight);

                Font font = new Font("Meiryo",Font.PLAIN,12);
                Font dateFont = new Font("Meiryo",Font.BOLD,12);

                for(int d = 0; d<dates.size(); d++)
                {
                    String date = dates.get(d);
                    int startX = leftMargin + layout.x(d);
                    int startY = topMargin + layout.y(d);

                    Map<String,List<String>> assignedDateMap = session.assignedShift.get(date);
                    if(assignedDateMap == null){assignedDateMap = new LinkedHashMap<>();}

                    //日付セル
                    g.setColor(new Color(221,235,247));
                    g.fillRect(startX,startY,layout.timeWidth,layout.rowHeight);
                    g.setColor(Color.BLACK);
                    g.setFont(dateFont);
                    g.drawString(date,startX+5,startY+18);
                    g.setFont(font);

                    int rowY = startY + layout.rowHeight;
                    for(String time : session.allTimes)
                    {
                        //時間帯セル
                        g.setColor(Color.WHITE);
                        g.fillRect(startX,rowY,layout.timeWidth,layout.rowHeight);
                        g.setColor(Color.BLACK);
                        g.drawRect(startX,rowY,layout.timeWidth,layout.rowHeight);
                        g.drawString(time,startX+5,rowY+18);

                        List<String> persons = assignedDateMap.get(time);
                        for(int i=0; i<session.slotLimit; i++)
                        {
                            int x = startX + layout.timeWidth + i*layout.personWidth;

                            g.setColor(Color.WHITE);
                            g.fillRect(x,rowY,layout.personWidth,layout.rowHeight);
                            g.setColor(Color.BLACK);
                            g.drawRect(x,rowY,layout.personWidth,layout.rowHeight);

                            if(persons != null && i<persons.size()){g.drawString(persons.get(i),x+5,rowY+18);}
                        }
                        rowY += layout.rowHeight;
                    }
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image,"png",baos);
                byte[] pngBytes = baos.toByteArray();

                String fileName = "shift.png";

                if(!dates.isEmpty())
                {
                    String firstDate = dates.get(0);
                    String lastDate = dates.get(dates.size()-1);
                    String[] firstParts = firstDate.split("/");
                    String[] lastParts = lastDate.split("/");
                    String firstName = null;
                    String lastName = null;

                    if(firstParts.length == 2)
                    {
                        int month = Integer.parseInt(firstParts[0]);
                        int day = Integer.parseInt(firstParts[1].replaceAll("\\(.*\\)",""));

                        firstName = String.format("%02d%02d",month,day);
                    }
                    if(lastParts.length == 2)
                    {
                        int month = Integer.parseInt(lastParts[0]);
                        int day = Integer.parseInt(lastParts[1].replaceAll("\\(.*\\)",""));

                        lastName = String.format("%02d%02d",month,day);
                    }
                    fileName = "shift_" + firstName + "~" + lastName + ".png";
                }
                exchange.getResponseHeaders().set("Content-Type","image/png");
                exchange.getResponseHeaders().set("Content-Disposition","attachment; fileName=" + fileName);
                exchange.sendResponseHeaders(200,pngBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(pngBytes);
                os.close();

                g.dispose();
            }
            catch(Exception e){e.printStackTrace();}
        });

        server.setExecutor(null);
        server.start();
        System.out.println("サーバー起動:http://localhost:8000");

    }
}

