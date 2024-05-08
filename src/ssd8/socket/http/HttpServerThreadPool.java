package ssd8.socket.http;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class <em>HttpServerThreadPool</em> is a class representing a simple HTTP server with thread pool.
 *
 * @author Sun Shuo
 */
public class HttpServerThreadPool {
    private final int PORT = 8996;
    private final int POOL_SIZE = 4;
    private static String root = null;

    ServerSocket serverSocket;
    ExecutorService executorService;

    /**
     * HttpServerThreadPool的构造函数;
     */
    public HttpServerThreadPool() throws IOException {
        // 初始化socket和线程池
        this.serverSocket = new ServerSocket(PORT);
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * POOL_SIZE);
        System.out.println("Server start, and listen to " + PORT + " port now");
    }

    /**
     * 主函数，读入根路径参数，没有参数则报错
     * @param args args[0] = root 根路径
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1){
            System.out.println("请传入根路径作为参数！");
        } else{
            root = args[0];
            File file = new File(root);
            if (file.exists() && file.isDirectory()){
                new HttpServerThreadPool().service();
            } else {
                System.out.println("输入的路径不是合法路径！");
            }
        }
    }

    /**
     * 启动服务， serverSocket 监听TCP的端口并等待连接。当一个客户端连接到来后会新建一个线程并提供服务
     */
    public void service(){
        Socket tcpSocket = null;

        while(true){
            try{
                tcpSocket = serverSocket.accept();
                executorService.execute(new HttpService(tcpSocket,root));
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
