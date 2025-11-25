import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.regex.*;

/* an HTTP teapot :-) 
 * Tristan Henderson, Oct 2025
 */

public class HTTPTeapot {

    static int port; 
    static ServerSocket server;
  
    private final static String USAGE = "Usage: java HTTPTeapot <port_number>";
                
    /* create a regex to parse headers as per RFC9112 */

    final static String headerRegex = "^(?<name>[\\w-]+):\\s*(?<value>.*)\\s*$";
  
    public static void main(String[] args) {
  
        /* check arguments */
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(0);
        } else {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println(USAGE);
                System.exit(0);
            }
        }
   
        /* start server */
        try {
            server = new ServerSocket(port); 
            System.out.println("Starting server: " + server.toString());
        } catch (IOException e) {
            System.err.println("IO Exception: " + e.getMessage());
        }
   
        while (true) { 
        
            try {
            
                /* wait for connection */
                Socket connection = server.accept(); 
                
                System.out.println("New connection: " + connection.getInetAddress());
            
                /* input and output */
                PrintWriter tx = new PrintWriter(connection.getOutputStream(), true);
                BufferedReader rx = new BufferedReader(new InputStreamReader(connection.getInputStream()));
   
                /* process the HTTP request */

                /* first line is the start-line (see RFC7230 sec 3) */
                String startLine = rx.readLine();
                System.out.println("HTTP start-line: " + startLine);
                
                /* next come the headers */ 

                Pattern p = Pattern.compile(headerRegex);

                /* loop over headers and put them into a HashMap */

                String input;
                HashMap<String, String> headers = new HashMap<>();
                while ((input = rx.readLine()) != null && !input.isEmpty()) {
                    Matcher m = p.matcher(input);
                    boolean found = m.find();
                    if (found) {
                        headers.put(m.group("name"),m.group("value"));
                    }
                }

                System.out.println("HTTP headers: " + headers);

                /* next is the body but we will ignore it for simplicity */

                /* return an inappropriate (?) HTTP status */
                /* note that HTTP uses CRLF line breaks, encoded as \r\n */

                String response = "HTTP/1.1 418 I'm a teapot\r\n\r\n\r\n";
                
                tx.println(response);
            
                connection.close();
            }
            
            // keep the server running; use Ctrl-C to quit the while loop
    
            catch (IOException e) {
                System.err.println("IO Exception: " + e.getMessage());
            }
        }
    }
}

