package play.modules.testrunner;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ConfirmHandler;
import com.gargoylesoftware.htmlunit.DefaultPageCreator;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.PromptHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebWindow;
import com.gargoylesoftware.htmlunit.javascript.host.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.Map;


public class FirePhoque {


    /**************************************************
    TestRun Class 
    **************************************************/


    /**
    *  TestRun class runs an individual 
    *  
    */
    private static class TestRun implements Callable<Map<String,Object>> {

        private String test = null;

        public TestRun(String testName) {
            this.test = testName;
        }

        public Map<String,Object> call() throws Exception {
            
            HashMap<String,Object> result = new HashMap<String,Object>();        
            File  root = new File(FirePhoque.FILENAME);
            Boolean ok = true;

            WebClient firephoque = FirePhoque.createWebClient();

            long start = System.currentTimeMillis();
            String testName = test.replace(".class", "").replace(".test.html", "").replace(".", "/").replace("$", "/"); 
            result.put("test",test);
            result.put("testName",testName);

            URL url;
            if (test.endsWith(".class")) {
                url = new URL(FirePhoque.APP + "/@tests/" + test);
            } else {
                url = new URL(FirePhoque.APP + "" + FirePhoque.SELENIUM + "?baseUrl=" + FirePhoque.APP + "&test=/@tests/" + test + ".suite&auto=true&resultsUrl=/@tests/" + test);
            }
            firephoque.openWindow(url, "headless");
            firephoque.waitForBackgroundJavaScript(5 * 60 * 1000);
            int retry = 0;
            while(retry < 5) {
                if (new File(root, test.replace("/", ".") + ".passed.html").exists()) {
                    result.put("testStatus","PASSED     ");
                    break;
                } else if (new File(root, test.replace("/", ".") + ".failed.html").exists()) {
                    result.put("testStatus","FAILED  !  ");
                    ok = false;
                    break;
                } else {
                    if(retry++ == 4) {
                        result.put("testStatus","ERROR   ?  ");
                        ok = false;
                        break;
                    } else {
                        Thread.sleep(1000);
                    }
                }
            }

            //
            int duration = (int) (System.currentTimeMillis() - start);
            result.put("testDuration", new Integer(duration));
            result.put("testSuccess", ok);

            return result;
        }

    }


    /**************************************************
    FirePhoque Class 
    **************************************************/

    public static String FILENAME = null;
    public static String SELENIUM = null;
    public static String APP = null;


    public static WebClient createWebClient() {        
        String headlessBrowser = System.getProperty("headlessBrowser", "INTERNET_EXPLORER_8");
        BrowserVersion browserVersion;
        if ("FIREFOX_3".equals(headlessBrowser)) {
            browserVersion = BrowserVersion.FIREFOX_3;
        } else if ("FIREFOX_3_6".equals(headlessBrowser)) {
            browserVersion = BrowserVersion.FIREFOX_3_6;
        } else if ("INTERNET_EXPLORER_6".equals(headlessBrowser)) {
            browserVersion = BrowserVersion.INTERNET_EXPLORER_6;
        } else if ("INTERNET_EXPLORER_7".equals(headlessBrowser)) {
            browserVersion = BrowserVersion.INTERNET_EXPLORER_7;
        } else {
            browserVersion = BrowserVersion.INTERNET_EXPLORER_8;
        }

        WebClient firephoque = new WebClient(browserVersion);
        firephoque.setPageCreator(new DefaultPageCreator() {

            @Override
            public Page createPage(WebResponse wr, WebWindow ww) throws IOException {
                Page page = createHtmlPage(wr, ww);
                return page;
            }
        });
        firephoque.setThrowExceptionOnFailingStatusCode(false);
        firephoque.setAlertHandler(new AlertHandler() {
            public void handleAlert(Page page, String string) {
                try {
                    Window window = (Window)page.getEnclosingWindow().getScriptObject();
                    window.custom_eval(
                        "parent.selenium.browserbot.recordedAlerts.push('" + string.replace("'", "\\'")+ "');"
                    );
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        firephoque.setConfirmHandler(new ConfirmHandler() {
            public boolean handleConfirm(Page page, String string) {
                try {
                    Window window = (Window)page.getEnclosingWindow().getScriptObject();
                    Object result = window.custom_eval(
                        "parent.selenium.browserbot.recordedConfirmations.push('" + string.replace("'", "\\'")+ "');" +
                        "var result = parent.selenium.browserbot.nextConfirmResult;" +
                        "parent.selenium.browserbot.nextConfirmResult = true;" +
                        "result"
                    );
                    return (Boolean)result;
                } catch(Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });
        firephoque.setPromptHandler(new PromptHandler() {
            public String handlePrompt(Page page, String string) {
                try {
                    Window window = (Window)page.getEnclosingWindow().getScriptObject();
                    Object result = window.custom_eval(
                        "parent.selenium.browserbot.recordedPrompts.push('" + string.replace("'", "\\'")+ "');" +
                        "var result = !parent.selenium.browserbot.nextConfirmResult ? null : parent.selenium.browserbot.nextPromptResult;" +
                        "parent.selenium.browserbot.nextConfirmResult = true;" +
                        "parent.selenium.browserbot.nextPromptResult = '';" +
                        "result"
                    );
                    return (String)result;
                } catch(Exception e) {
                    e.printStackTrace();
                    return "";
                }
            }
        });
        firephoque.setThrowExceptionOnScriptError(false);        

        return firephoque;
    } 



    /**************************************************
    Main 
    **************************************************/


    public static void main(String[] args) throws Exception {

        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);

        FirePhoque.APP = System.getProperty("application.url", "http://localhost:9000");


        // Tests description

        List<String> tests = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new URL(FirePhoque.APP + "/@tests.list").openStream(), "utf-8"));
            String marker = in.readLine();
            if (!marker.equals("---")) {
                throw new RuntimeException("Oops");
            }
            FILENAME = in.readLine();
            SELENIUM = in.readLine();
            tests = new ArrayList<String>();
            String line;
            while ((line = in.readLine()) != null) {
                tests.add(line);
            }
            in.close();
        } catch(Exception e) {
            System.out.println("~ The application does not start. There are errors: " + e);
            System.exit(-1);
        }

        // get the browser
        //
        WebClient firephoque = FirePhoque.createWebClient();
        
        // Go!
        //
        System.out.println("**** LENDUP TESTRUNNER ****");

        // find the max length for pretty print
        //
        int maxLength = 0;
        for (String test : tests) {
            String testName = test.replace(".class", "").replace(".test.html", "").replace(".", "/").replace("$", "/");
            if (testName.length() > maxLength) {
                maxLength = testName.length();
            }
        } 
        
        // print the number of tests
        //       
        System.out.println("~ " + tests.size() + " test" + (tests.size() != 1 ? "s" : "") + " to run:");
        System.out.println("~");

    

        // init the framework
        //
        firephoque.openWindow(new URL(FirePhoque.APP + "/@tests/init"), "headless");
        

        boolean ok = true;
        Integer threadPool = Integer.parseInt(System.getProperty("threadpool.size", "6"));
        System.out.println("**** THREAD POOL: " + threadPool + " ****");

        
        ExecutorService executor = Executors.newFixedThreadPool(threadPool);
        List<Future<Map<String,Object>>> list = new ArrayList<Future<Map<String,Object>>>();

        // create a test run for each test
        // and then send to executor
        //
        for (String test : tests) {

            // skip the special clean test
            if("unit.CleanDataFixturesTest.class".equals(test)) {
                continue;
            }
            Callable<Map<String,Object>> worker = new TestRun(test);
            Future<Map<String,Object>> submit = executor.submit(worker);
            list.add(submit);

        }

        // shutdown the executor
        executor.shutdown();

        // failed list
        List<String> failedTestList = new ArrayList<String>();
        failedTestList.add("unit.CleanDataFixturesTest.class");

        // now retrieve the result
        //
        for (Future<Map<String,Object>> future : list) {
          try {
            Map<String,Object> result = future.get();
            String test = (String) result.get("test");
            String testName = (String) result.get("testName");
            String status = (String) result.get("testStatus");
            Boolean success = (Boolean) result.get("testSuccess");
            Integer duration = (Integer) result.get("testDuration");

            if(!success) {
                failedTestList.add(test);
                ok = false;
            }

            System.out.print("~ " + testName + "... ");
            for (int i = 0; i < maxLength - testName.length(); i++) {
                System.out.print(" ");
            }
            System.out.print("    ");
            System.out.print(status);            
            int seconds = (duration / 1000) % 60;
            int minutes = (duration / (1000 * 60)) % 60;
            if (minutes > 0) {
                System.out.println(minutes + " min " + seconds + "s");
            } else {
                System.out.println(seconds + "s");
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          } catch (ExecutionException e) {
            e.printStackTrace();
          }
        }


        if(failedTestList.size() > 1) { 
            ok = true;
            System.out.println("\n~ Re-running failed test...");
            System.out.println("~");

            ExecutorService executorSingle = Executors.newSingleThreadExecutor();
            List<Future<Map<String,Object>>> listSingle = new ArrayList<Future<Map<String,Object>>>();

            for (String test : failedTestList) {
                Callable<Map<String,Object>> worker = new TestRun(test);
                Future<Map<String,Object>> submit = executorSingle.submit(worker);
                listSingle.add(submit);
            }

            // shutdown the executor
            executorSingle.shutdown();

            for (Future<Map<String,Object>> future : listSingle) {
              try {
                Map<String,Object> result = future.get();
                String test = (String) result.get("test");
                String testName = (String) result.get("testName");
                String status = (String) result.get("testStatus");
                Boolean success = (Boolean) result.get("testSuccess");
                Integer duration = (Integer) result.get("testDuration");

                if(!success) {
                    ok = false;
                }

                System.out.print("~ " + testName + "... ");
                for (int i = 0; i < maxLength - testName.length(); i++) {
                    System.out.print(" ");
                }
                System.out.print("    ");
                System.out.print(status);            
                int seconds = (duration / 1000) % 60;
                int minutes = (duration / (1000 * 60)) % 60;
                if (minutes > 0) {
                    System.out.println(minutes + " min " + seconds + "s");
                } else {
                    System.out.println(seconds + "s");
                }
              } catch (InterruptedException e) {
                e.printStackTrace();
              } catch (ExecutionException e) {
                e.printStackTrace();
              }
            }

        }


        firephoque.openWindow(new URL(FirePhoque.APP + "/@tests/end?result=" + (ok ? "passed" : "failed")), "headless");

    }
}
