package com.anvil.cli.commands;

import com.anvil.server.AnvilApplication;
import com.anvil.server.rpc.JsonRpcStdioServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 服务启动命令：以 HTTP 和/或 JSON-RPC stdio 两种方式启动 Anvil App Server。
 *
 * <ul>
 *   <li>默认模式：启动 Spring Boot HTTP 服务（REST + SSE）。</li>
 *   <li>{@code --stdio}：不启用 HTTP，改为通过标准输入/输出提供 JSON-RPC 协议服务。</li>
 * </ul>
 */
@Command(name = "serve", description = "Start Anvil App Server (HTTP and/or JSON-RPC stdio)")
public class ServeCommand implements Callable<Integer> {

    /** HTTP 服务端口（默认 7788）。 */
    @Option(names = "--port", defaultValue = "7788", description = "HTTP port")
    int port;

    /** 以 JSON-RPC over stdin/stdout 模式运行（不启动 HTTP）。 */
    @Option(names = "--stdio", description = "Run JSON-RPC over stdin/stdout (no HTTP)")
    boolean stdio;

    /**
     * 启动服务器。
     *
     * <p>stdio 模式会一直阻塞读取 stdin，直到客户端关闭输入流；
     * 随后关闭应用上下文并退出。默认 HTTP 模式则长期驻留。</p>
     *
     * @return 退出码 0
     */
    @Override
    public Integer call() throws Exception {
        SpringApplication app = new SpringApplication(AnvilApplication.class);
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", port);
        app.setDefaultProperties(props);

        if (stdio) {
            app.setWebApplicationType(WebApplicationType.NONE);
            ConfigurableApplicationContext ctx = app.run(new String[0]);
            JsonRpcStdioServer rpc = ctx.getBean(JsonRpcStdioServer.class);
            rpc.run(System.in, System.out);
            ctx.close();
            return 0;
        }

        app.run(new String[0]);
        return 0;
    }
}
