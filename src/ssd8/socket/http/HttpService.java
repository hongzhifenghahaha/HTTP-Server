package ssd8.socket.http;

import java.io.*;
import java.net.Socket;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Class <em>HttpServerThreadPool</em> is a class provides the http service
 *
 * @author Sun Shuo
 */
public class HttpService implements Runnable {
    private static String CRLF = "\r\n";
    private static int buffer_size = 8192;

    private String root;
    private String response;

    private Socket socket;
    private BufferedInputStream istream;
    private BufferedOutputStream ostream;
    private ArrayList<String> headerLine;

    private SimpleDateFormat sdf;
    private Date currentTime;

    /**
     * HttpService 构造函数
     *
     * @param socket server socket
     * @param root   根路径
     */
    public HttpService(Socket socket, String root) {
        this.socket = socket;
        this.root = root;
        headerLine = new ArrayList<String>();
        /**
         * 使用 SimpleDateFormat 来格式化时间
         */
        currentTime = new Date();
        sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    /**
     * 初始化IO流
     */
    public void initStream() throws IOException {
        this.istream = new BufferedInputStream(socket.getInputStream());
        this.ostream = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * 处理来自客户端的请求
     */
    @Override
    public void run() {
        try {
            // 初始化IO流
            initStream();
            // 打印连接信息
            System.out.println("新的tcp连接，来自: " + socket.getInetAddress() + "：" + socket.getPort());

            // 读取 request 报文
            readRequest();

            // 判断 header 是否为空
            if (headerLine == null || headerLine.isEmpty()) {
                response = "HTTP/1.1 400 Bad Request" + CRLF + CRLF;
                sendResponse();
            } else {
                //判断第一行是不是GET和PUT之外的请求
                String method = headerLine.get(0).split("\\s+")[0];
                if (!"GET".equals(method) && !"PUT".equals(method)) {
                    // 不是GET或PUT直接返回错误 400
                    response = "HTTP/1.1 400 Bad Request" + CRLF + CRLF;
                    sendResponse();
                } else {
                    String filename = null;
                    for (String line : headerLine) {
                        // 处理GET请求
                        if (line.startsWith("GET")) {
                            doGET(line);
                        }
                        // 处理PUT请求，获取文件名
                        else if (line.startsWith("PUT")) {
                            filename = doPUT(line);
                        }
                        // 处理PUT请求，获取内容的长度，
                        else if (line.startsWith("Content-Length")) {
                            if (null == filename) {
                                break;
                            }
                            // 存储内容长度为 length 的文件
                            int length = Integer.parseInt(line.split("\\s+")[1].trim());
                            if (length > 0) {
                                putFile(filename, length);
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                // 关闭流
                istream.close();
                ostream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (null != socket) {
                try {
                    // 关闭socket
                    System.out.println("port " + socket.getPort() + " : connection closed");
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 读取来自客户端的请求，并将他们存储在headerLine中
     */
    public void readRequest() throws IOException {
        int last = 0, c = 0;
        StringBuilder header = new StringBuilder();
        // 逐字符读入
        while ((c = istream.read()) != -1) {
            // 如果是 \r 则继续读入
            if (c == '\r') {
                continue;
            } else if (c == '\n') {
                // 如果连续读入两次 \r\n ，则代表 header 已经结束，可以停止读入了。
                if (c == last) {
                    break;
                }
                // header添加换行符
                last = c;
                header.append("\n");
                headerLine.add(header.toString());
                header = new StringBuilder();
            } else {
                // 把 c 添加到header中
                last = c;
                header.append((char) c);
            }
        }
    }

    /**
     * 处理 GET 请求
     *
     * @param line 以 GET 开头的行
     */
    public void doGET(String line) throws IOException {
        String[] fields = line.split("\\s+");
        // 请求的第一行的参数不是三个，则是一个错误的请求
        if (fields.length != 3) {
            response = "HTTP/1.1 400 Bad Request" + CRLF + CRLF;
            sendResponse();
            return;
        }

        // 获取文件路径
        String filename = fields[1];
        File file = new File(root + File.separator + filename);
        //  如果文件不存在或者不是文件，则返回 404 错误页
        if (!(file.exists() && file.isFile())) {
            File file404 = new File(root + File.separator + "404.html");
            BufferedInputStream bis404 = new BufferedInputStream(new FileInputStream(file404));

            response = "HTTP/1.1 404 Not Found" + CRLF;
            // 添加一些附加信息到 response 中
            response = appendInformationToResponse(response, file404);
            // 返回 response 给客户端
            sendResponse();

            // 向客户端传输文件
            writeFileToClient(bis404);
        } else {
            if (!file.canRead() || !file.canWrite()) {
                File file403 = new File(root + File.separator + "403.html");
                BufferedInputStream bis403 = new BufferedInputStream(new FileInputStream(file403));

                response = "HTTP/1.1 403 Forbidden" + CRLF;
                response = appendInformationToResponse(response, file403);
                sendResponse();

                writeFileToClient(bis403);
            } else {
                response = "HTTP/1.1 200 OK" + CRLF;
                response = appendInformationToResponse(response, file);
                sendResponse();

                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                writeFileToClient(bis);
            }
        }
    }

    /**
     * 处理 post 请求，获取文件名
     *
     * @param line 以 PUT 开始的行
     * @return
     */
    public String doPUT(String line) throws IOException {
        String[] fields = line.split("\\s+");
        // 请求的第一行的参数不是三个，则是一个错误的请求
        if (fields.length != 3) {
            System.out.println("Get a Bad Request");
            response = "HTTP/1.1 400 Bad Request" + CRLF + CRLF;
            sendResponse();
            return null;
        }

        // 获取文件路径
        String filename = fields[1];
        File file = new File(root + File.separator + filename);
        // 如果要 PUT 的文件已存在，则会更新该文件
        if (file.exists()) {
            System.out.println("File updated: " + file.getName());
            response = "HTTP/1.1 200 OK" + CRLF;
            response = appendInformationToResponse(response, file);
            sendResponse();
        } else {
            // 如果创建文件成功，则返回 201 Created
            if (tryCreateNewFile(filename)) {
                response = "HTTP/1.1 201 Created" + CRLF;
                response = appendInformationToResponse(response, file);
                sendResponse();
            }
            // 如果创建文件失败，则返回 500 Internal Server Error
            else {
                response = "HTTP/1.1 500 Internal Server Error" + CRLF;
                response = appendInformationToResponse(response, file);
                sendResponse();
                return null;
            }
        }
        // 返回文件名
        return filename;
    }

    /**
     * 从客户端接收文件，并存储到 server_dir 文件夹中
     *
     * @param filename the filename of the file to be created or updated
     * @param length   byte length of the file received from client
     */
    public void putFile(String filename, int length) throws IOException {
        // 定义字节数组大小和文件路径
        byte[] buffer = new byte[buffer_size];
        File putFile = new File(root + File.separator + filename);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(putFile));
        try {
            while (length > 0) {
                // 读取来自客户端的文件字节。
                int read = istream.read(buffer, 0, Math.min(length, buffer_size));
                if (read == -1) {
                    break;
                }
                // 将从客户端读取到的内容写入到文件中，每次写入 buffer.length
                bos.write(buffer, 0, read);
                length -= buffer_size;
            }
        } finally {
            bos.close();
        }
    }

    /**
     * 为 response 附加额外的信息，比如 Date， Server 等字段到 response 中
     *
     * @param response 响应
     * @param file     传回给客户端的文件，这里的作用是用于获取文件的 MIME 类型
     */
    public String appendInformationToResponse(String response, File file) {
        response += "Date: " + sdf.format(currentTime) + CRLF +
                "Server: VWebServer" + CRLF +
                "X-Frame-Options: SAMEORIGIN" + CRLF +
                "Connection: close" + CRLF +
                "Content-Type: " + URLConnection.guessContentTypeFromName(file.getName()) +
                "; charset=iso-8858-1" + CRLF +
                "Content-Length: " + file.length() + CRLF + CRLF;
        return response;
    }

    /**
     * 将 response 返回给客户端
     */
    public void sendResponse() throws IOException {
        byte[] buffer; //必须 new 一个新的byte[]，否则之前未读完的缓冲区不会被清空，反而是被追加读取。
        buffer = response.getBytes();
        ostream.write(buffer);
        ostream.flush();
    }

    /**
     * 将文件写到客户端
     *
     * @param bis 文件输入流
     */
    public void writeFileToClient(BufferedInputStream bis) throws IOException {
        byte[] buffer = new byte[buffer_size]; //必须new一个新的byte[]，否则之前未读完的缓冲区不会被清空，反而是被追加读取。
        // 从文件输入流中获取字节，写入到 socket 输出流中
        while (bis.read(buffer) > 0) {
            ostream.write(buffer);
        }
        ostream.flush();
        bis.close();
    }

    /**
     * 尝试创建新文件
     *
     * @param filename 即将被创建的文件的文件名
     * @return 文件是否能被创建或者更新
     */
    public boolean tryCreateNewFile(String filename) {
        // 获取文件路径和该文件的上级目录
        File file = new File(root + File.separator + filename);
        File parentFile = file.getParentFile();
        // 如果上级目录不存在则创建目录
        if (!parentFile.exists()) {
            if (parentFile.mkdirs()) {
                System.out.println("Create " + parentFile.getName() + " succeed");
            } else {
                System.out.println("Create " + parentFile.getName() + " failed");
                return false;
            }
        }

        try {
            // 创建新文件，成功返回true
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
                return true;
            }
            // 创建文件失败则返回false
            else {
                System.out.println("File updated: " + file.getName());
                return true;
            }
        } catch (IOException e) {
            // 输出创建文件时发生错误
            System.out.println("An error occurred while creating new file");
            e.printStackTrace();
            return false;
        }
    }

}
