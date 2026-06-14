//Mavenで使うもの
package com.example;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;



//javaで使うもの
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;

class Person
{
    String grade;
    String name;

    Map<String,List<String>>Times = new LinkedHashMap<>();
}

public class makeShift
{
    //担当者を保存する場所
    static Map<String,Map<String,List<String>>> assignedShift = new LinkedHashMap<>();
    //最後に読み込んだURL保存場所
    static String lastSheetUrl = "";
    //最後に読み込んだスプレッドシートIDの保存場所
    static String currentSheetId = "";
    //人数上限，回数上限の設定
    static int slotLimit = 2;
    static int dailyLimit =  2;
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
    
    //一時保存のためのメソッド
    public static void saveShift()
    {
        try
        {
            //初日の日付を取得
            String earliestDate = "";
            for(String date : assignedShift.keySet())
            {    
                if(earliestDate.isEmpty() || date.compareTo(earliestDate) < 0)
                    {earliestDate = date;}
            }
            //ファイル名を「shift_(初日の日付)_dat」に設定
            String[] parts = earliestDate.split("/");
            String formattedDate = earliestDate;
            if(parts.length == 2)
            {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                formattedDate = String.format("%02d%02d", month,day);
            }
            String fileName ="shift_" + formattedDate + "_" + currentSheetId + ".dat";
            //ファイルを出力
            ObjectOutputStream out =new ObjectOutputStream(new FileOutputStream(fileName));
            out.writeObject(assignedShift);
            out.close();
            System.out.println("保存:" + fileName);
        }
        catch(Exception e){e.printStackTrace();}
    }
    //保存済みページを読み込むためのメソッド
    @SuppressWarnings("unchecked")
    public static void loadShift(String fileName)
    {
        try
        {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileName));
            assignedShift = (Map<String,Map<String,List<String>>>)in.readObject();
            in.close();
            System.out.println("読み込み" + fileName);
        }
        catch(Exception e){e.printStackTrace();}
    }
    public static void main(String[]args) throws IOException
    {
        //サーバーを立てる
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8000"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port),0);

        //URL入力前のブラウザ画面の構成
        server.createContext("/",(HttpExchange exchange) ->
        {
            String response =
                "<html>" + 
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<title>シフト作成</title>" +
                "</head>" + 
                "<body>" +
                "<form method='POST' action='/submit'>" +
                "<input type='text' " +
                "name='sheetUrl' " +
                "size='80' " +
                "placeholder='Google SpreadsheetのURLを入力してください。'> " +
                "<br><br>" +
                
                "各時間帯の最大人数を整数で入力してください。" + 
                "<input type='number' " + 
                "name='slotLimit' " + 
                "value='2' " + 
                "min='1'>" +
                "<br><br>" +

                "同じ日の最大担当回数を整数で入力してください。" + 
                "<input type='number' " +
                "name='dailyLimit' " +
                "value='2' " +
                "min='1'>" +

                "<input type='submit' value='送信'>" +
                "</form>" +
                "<br><br>" +
                "<a href='saved'>作成中・作成済みのシフト表を開く</a>" +
                "</body>" + 
                "</html>";

            exchange.sendResponseHeaders(200,response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });

        //保存済みファイル一覧ページの構成
        server.createContext("/saved",(HttpExchange exchange) ->
            {
                StringBuilder fileList = new StringBuilder();
                java.io.File folder = new java.io.File(".");
                for(java.io.File file : folder.listFiles())
                {
                    if(file.getName().startsWith("shift_") && file.getName().endsWith(".dat"))
                    {
                        fileList.append(file.getName() + 
                            "<form method='POST' action='/load' style='display:inline;'>" +
                            "<input type='hidden' name='fileName' value='" +
                            file.getName() + "'>" +
                            "<input type='submit' value='開く'>" +
                            "</form><br>"
                        );   
                        
                    }
                }
                //HTMLで表示
                String response=
                    "<html>" +
                    "<head>" +
                    "<mata-charset='UTF-8'>" +
                    "<title>保存済みシフト表一覧</title>" +
                    "</head>" +
                    "<body>" +
                    "<h2>保存済みシフト表一覧</h2>" +
                    fileList.toString() +
                    "<br><br>" +
                    "<a href='/'>新しいシフト表を作成する</a>" +
                    "</body>" +
                    "</html>";

                    exchange.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200,response.getBytes(StandardCharsets.UTF_8).length);

                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
            }
        );

        //「開く」押下後表示される/loadページ
        server.createContext("/load",(HttpExchange exchange) -> 
            {
                InputStream is = exchange.getRequestBody();
                String data = new String(is.readAllBytes(),StandardCharsets.UTF_8);

                Map<String,String> formData = parseFormData(data);

                String fileName = formData.get("fileName");
                loadShift(fileName);

                exchange.getResponseHeaders().set("Location","/");
                exchange.sendResponseHeaders(302,-1);
                exchange.close();

            }
        
        );

        //URL入力後のブラウザ画面の構成
        server.createContext("/submit",(HttpExchange exchange) ->
            {
                InputStream is = exchange.getRequestBody();
                String data = new String(is.readAllBytes(),StandardCharsets.UTF_8);

                Map<String,String> formData = parseFormData(data);
                String sheetUrl = formData.get("sheetUrl");
                lastSheetUrl = sheetUrl;
                slotLimit = Integer.parseInt(formData.get("slotLimit"));
                dailyLimit = Integer.parseInt(formData.get("dailyLimit"));
                
                String[] firstSplit = sheetUrl.split("/d/");
                if(firstSplit.length<2){throw new IllegalArgumentException("適切なURLを入力してください。");}
                String afterD = firstSplit[1];
                String[] secondSplit = afterD.split("/");
                String sheetId = secondSplit[0];
                currentSheetId = sheetId;

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

                String[] rows = csvData.toString().split("\n");
                //ヘッダーを取り出す
                if(rows.length == 0){ throw new RuntimeException("データが存在しません。");}
                String header = rows[0];
                //人ごとに読み込む
                List<Person> persons = new ArrayList<>();

                //ヘッダーを解析
                List<String> headerColumns = parseCsvLine(header);
                //表示順を固定するためのリスト生成
                List<String> allTimes = new ArrayList<>();
                //人別に処理
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
                                if(!time.equals("なし")){times.add(time);}
                            }
                        }

                        person.Times.put(date,times);
                    }

                    persons.add(person);
                }
                
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
                                if(!allTimes.contains("追い出し(左)")){allTimes.add("追い出し(左)");}
                                if(!allTimes.contains("追い出し(右)")){allTimes.add("追い出し(右)");}
                            }
                            else
                            {
                                if(!allTimes.contains(time)){allTimes.add(time);}
                            }
                        }
                    }   
                }

                

                //表を保存するためのMap生成
                Map<String,Map<String,List<String>>> shiftTable = new LinkedHashMap<>();
                //全員に対し処理を行う
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
                
                //日別にボタンとプレビューを扱う用
                Map<String,StringBuilder> shiftResultMap = new LinkedHashMap<>();
                Map<String,StringBuilder> previewTableMap = new LinkedHashMap<>();
                
                //表生成の確認用
                for(String date : shiftTable.keySet())
                {
                    StringBuilder shiftResult = new StringBuilder();

                    shiftResult.append("<h3>");
                    shiftResult.append("</h3>");

                    Map<String,List<String>> dateMap = shiftTable.get(date);

                    //ボタンの動作に関するコード
                    for(String time : allTimes)
                    {
                        shiftResult.append("<b>");
                        shiftResult.append(time);
                        shiftResult.append("</b><br>");
                        //nullエラー対策
                        List<String> candidates = dateMap.get(time);
                        //担当者の表示
                        List<String> assigned = new ArrayList<>();
                        if(assignedShift.containsKey(date))
                        {
                            Map<String,List<String>> assignedDate = assignedShift.get(date);
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
                                
                                int assignedCount = 0;
                                if(assignedShift.containsKey(date))
                                {
                                    Map<String,List<String>> assignedDate = assignedShift.get(date);

                                    for(List<String> assignedPersons : assignedDate.values())
                                        {
                                            if(assignedPersons.contains(personName)){assignedCount++;}
                                        } 
                                }
                                
                                
                                boolean selected = assigned != null && assigned.contains(personName);
                                boolean full = assigned != null && assigned.size() >=slotLimit;

                                boolean overDaily = assignedCount >= dailyLimit;
                                
                                
                                //追い出し関連
                                boolean selectedOtherOidashi = false;

                                if(time.equals("追い出し(左)"))
                                {
                                    Map<String,List<String>> assignedDate = assignedShift.get(date);
                                    if(assignedDate != null)
                                    {
                                        List<String> rightAssigned = assignedDate.get("追い出し(右)");
                                        if(rightAssigned != null && rightAssigned.contains(personName)){selectedOtherOidashi = true;}   
                                    }
                                }
                                if(time.equals("追い出し(右)"))
                                {
                                    Map<String,List<String>> assignedDate = assignedShift.get(date);
                                    if(assignedDate != null)
                                    {
                                        List<String> leftAssigned = assignedDate.get("追い出し(左)");
                                        if(leftAssigned != null && leftAssigned.contains(personName)){selectedOtherOidashi = true;}
                                    }
                                }

                                String disabled = "";
                                
                                if((full && !selected) || (overDaily && !selected) || selectedOtherOidashi){disabled = " disabled";}

                                String color;
                                if(selected){color = "#808080";}
                                else if(full || overDaily || selectedOtherOidashi){color = "#D3D3D3";}
                                else{color = "#FFFFFF";}

                                shiftResult.append(
                                    "<button type='button'" + 
                                    "onclick=\"assignedPerson('" + date + "','" + time + "','" + personName +  "')\"" +
                                    "style='background:" + color + ";'" + disabled + ">" + personName + "(" + grade + ")" +
                                    "</button>" );

                            }
                            shiftResult.append("<br><br>");
                        }
                        
                    }
                    shiftResult.append("<br>");
                    shiftResultMap.put(date,shiftResult);
                }

                //シフト表作成
                for(String date : shiftTable.keySet())
                {
                StringBuilder previewTable = new StringBuilder();

                    previewTable.append("<table border='1'>");

                    previewTable.append("<tr>");
                    previewTable.append("<th></th>");

                    for(int i = 1; i <= slotLimit; i++){previewTable.append("<th></th>");}

                    previewTable.append("</tr>");

                    for(String time : allTimes)
                    {
                        List<String> assignedPersons = new ArrayList<>();

                        if(assignedShift.containsKey(date))
                        {
                            Map<String,List<String>> dateMapAssigned = assignedShift.get(date);
                            if(dateMapAssigned.containsKey(time)){assignedPersons = dateMapAssigned.get(time);}
                        }

                        previewTable.append("<tr>");

                        previewTable.append("<td>");
                        previewTable.append(time);
                        previewTable.append("</td>");

                        for(int i = 0; i < slotLimit; i++)
                        {
                            previewTable.append("<td>");
                            if(i < assignedPersons.size()){previewTable.append(assignedPersons.get(i));}
                            previewTable.append("</td>");
                        }

                        previewTable.append("</tr>");
                    }

                    previewTable.append("</table><br></br>");
                    previewTableMap.put(date,previewTable);
                }

                StringBuilder allShiftTables = new StringBuilder();
                allShiftTables.append("<div>");

                //エクセル出力用ボタン生成
                allShiftTables.append(
                    "<form method='POST' action='/export'>" +
                    "<input type='submit' value='Excel出力'>" +
                    "</form><br>"
                );
                
                boolean firstButton = true;
                
                for(String date : shiftTable.keySet())
                {
                    String tabId = "tab" + date.hashCode();

                    String bgColor = firstButton ? "#808080" : "#f0f0f0";
                    
                    allShiftTables.append
                    (
                        "<button type='button' class='tabButton'" + "id='btn_" + tabId + "' " + "onclick=\"openTab('" + tabId +"','btn_" + tabId + "')\" " +
                        "style='padding:8px 16px;" +
                        "border:1px solid #888;" +
                        "background:" + bgColor + ";" +
                        "margin-right:2px;" +
                        "cursor:pointer;'>" +
                        date + 
                        "</button>"
                    );
                    firstButton = false;
                }
                allShiftTables.append("</div><br>");

                boolean firstTab = true;
                
                for(String date : shiftTable.keySet())
                {
                    String display = firstTab ? "block" : "none" ;
                    String tabId = "tab" + date.hashCode();
                    allShiftTables.append(
                        "<div id='" +
                        tabId +
                        "' class='tabcontent' style='display:" +
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
                    allShiftTables.append("<h3>シフト表確認</h3>");
                    allShiftTables.append(shiftResultMap.get(date));
                    allShiftTables.append("</div>");

                    allShiftTables.append("<div style='border:1px solid:#ccc;" + "padding:10px;'>");
                    allShiftTables.append("<h3>シフト表プレビュー</h3>");
                    allShiftTables.append(previewTableMap.get(date));
                    allShiftTables.append("</div>");

                    allShiftTables.append("</div>");

                    firstTab = false;
                }

                //ブラウザ側への返答用HTML
                String response =
                    "<html>" +

                    //JavaScript
                    "<head>" +
                    "<script>" +
                    "function openTab(tabId,buttonId){" +
                        "console.log(tabId);" +
                        "console.log(buttonId);" +
                        "var tabs=document.getElementsByClassName('tabcontent');" +
                        "for(var i=0;i<tabs.length;i++){" + "tabs[i].style.display='none';}" + 
                        "var buttons = document.getElementsByClassName('tabButton');" +
                        "for(var i=0;i<buttons.length;i++){buttons[i].style.background='#f0f0f0';}" +
                        "document.getElementById(tabId).style.display='block';" + 
                        "document.getElementById(buttonId).style.background='#808080';" +
                    "}" +
                    "async function assignedPerson(date,time,person){" +
                        "const params =" + 
                        "'date='+encodeURIComponent(date)" +
                        "+'&time='+encodeURIComponent(time)" +
                        "+'&person='+encodeURIComponent(person);" +

                        "await fetch('/assign'," +
                            "{" +
                                "method:'POST'," +
                                "headers:" +
                                "{" +
                                    "'Content-Type':" +
                                    "'application/x-www-form-urlencoded'" +
                                "}," +
                                "body:params" +
                            "}" +
                        ");" +
                    "location.reload();" +
                    "}" +

                    "</script>" +
                    "</head>" +

                    "<body>" +
                    "<p>上限人数:" + slotLimit + "</p>" + 
                    "<p>上限回数:" + dailyLimit + "</p>" +
                    allShiftTables.toString() +
                    "</body>" +
                    "</html>";

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200,response.getBytes().length);
            
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

            }
        );

        server.createContext("/assign",(HttpExchange exchange) ->
        {
            InputStream is = exchange.getRequestBody();

            String data = new String(is.readAllBytes(),StandardCharsets.UTF_8);

            Map<String,String> formData = parseFormData(data);
            
            String date = formData.get("date");
            String time = formData.get("time");
            String person = formData.get("person");

            if(!assignedShift.containsKey(date)){assignedShift.put(date,new LinkedHashMap<>());}
            if(!assignedShift.get(date).containsKey(time)){assignedShift.get(date).put(time,new ArrayList<>());}
            
            
            List<String> assignedList = assignedShift.get(date).get(time);
            
            //その日の最大担当回数を記録
            int assignedCount = 0;
            if(assignedShift.containsKey(date))
            {
                Map<String,List<String>> assignedDate = assignedShift.get(date);

                for(List<String> persons : assignedDate.values())
                {if(persons.contains(person)){assignedCount++;}}
            }

            //登録済みなら解除する
            if(assignedList.contains(person)){assignedList.remove(person);}
            //未登録なら登録する
            else if((assignedList.size() < slotLimit) && (assignedCount < dailyLimit)){assignedList.add(person);}
            //データを自動保存
            saveShift();

            String response= "OK";

                exchange.getResponseHeaders().set("Content-type","text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200,response.getBytes(StandardCharsets.UTF_8).length);

                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(StandardCharsets.UTF_8));
                os.close();

        });

        //シフト表をExcelで出力する用
        server.createContext("/export",(HttpExchange exchange) ->
        {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("シフト表");

            //日付用のスタイル
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            dateStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            //ヘッダーの設定
            int rowIndex = 0;

            for(String date : assignedShift.keySet())
            {
                //日付の表示
                Row dateRow = sheet.createRow(rowIndex++);
                Cell dateCell = dateRow.createCell(0);
                dateCell.setCellValue(date);
                dateCell.setCellStyle(dateStyle);
                //時間帯の表示
                Map<String,List<String>> dateMap = assignedShift.get(date);
                for(String time : dateMap.keySet())
                {
                    Row row = sheet.createRow(rowIndex++);
                    Cell timeCell = row.createCell(0);
                    timeCell.setCellValue(time);
                    List<String> persons = dateMap.get(time);
                    for(int i = 0; i<persons.size(); i++)
                    {
                        Cell personCell = row.createCell(i + 1);
                        personCell.setCellValue(persons.get(i));
                    }
                }
                rowIndex++;
            }

            //列幅自動調整用のおまじない
            int columnCount = slotLimit + 1;
            for(int col = 0; col<columnCount; col++)
            {
                sheet.autoSizeColumn(col);
                int width = sheet.getColumnWidth(col);
                sheet.setColumnWidth(col,Math.min(width + 1000,255*266));
            }

            //作成したExcelをバイト列に変換する
            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            workbook.write(boas);
            byte[] excelBytes = boas.toByteArray();

            //HTTPのヘッダー設定のためのおまじない
            exchange.getResponseHeaders().set("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=shift.xlsx");             

            //ファイルをブラウザへ送信
            exchange.sendResponseHeaders(200,excelBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(excelBytes);
            os.close();

            workbook.close();  
        });
        

        server.setExecutor(null);
        server.start();
        System.out.println("サーバー起動:http://localhost:8000");

    }
}

