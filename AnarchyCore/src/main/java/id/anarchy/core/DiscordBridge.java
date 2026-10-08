package id.anarchy.core;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

public class DiscordBridge {
    private final JavaPlugin plugin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final BlockingQueue<String> queue=new LinkedBlockingQueue<>(200);
    private ScheduledExecutorService exec;
    private volatile boolean running;
    private String webhook="";
    private String serverName="Server";
    private String avatarTpl="";
    DiscordBridge(JavaPlugin plugin){this.plugin=plugin;}
    void start(){
        var c=plugin.getConfig();
        if(!c.getBoolean("discord.enabled",false))return;
        webhook=c.getString("discord.webhook-url","").trim();
        serverName=c.getString("discord.server-name","Server");
        avatarTpl=c.getString("discord.avatar-url","").trim();
        running=true;
        exec=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AnarchyCore-Discord");t.setDaemon(true);return t;});
        exec.execute(this::sendLoop);
    }
    void stop(String status){if(status!=null)event(status);running=false;if(exec!=null)exec.shutdownNow();}
    void event(String message){if(!running||message==null||message.isBlank())return;queue.offer(message.length()>1900?message.substring(0,1900):message);}
    private void sendLoop(){
        while(running){
            try{
                String msg=queue.poll(1,TimeUnit.SECONDS);
                if(msg==null||webhook.isBlank())continue;
                JsonObject body=new JsonObject();body.addProperty("username",serverName);body.addProperty("content",msg);
                String avatar=avatarTpl.replace("{uuid}",UUID.randomUUID().toString());
                if(!avatar.isBlank())body.addProperty("avatar_url",avatar);
                HttpRequest req=HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                http.send(req,HttpResponse.BodyHandlers.discarding());
            }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            catch(Exception e){plugin.getLogger().warning("Discord bridge error: "+e.getMessage());}
        }
    }
}
