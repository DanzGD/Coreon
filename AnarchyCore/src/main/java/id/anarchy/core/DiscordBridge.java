package id.anarchy.core;

import com.google.gson.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** Native, dependency-free Discord integration for AnarchyCore. */
public class DiscordBridge {
    private final JavaPlugin plugin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final BlockingQueue<Payload> queue=new LinkedBlockingQueue<>(300);
    private ExecutorService exec;
    private volatile boolean running;
    private String webhook="",serverName="Server",avatar="",footer="AnarchyCore Discord";
    private int color=0x5865F2;
    private record Payload(String content,String title,String description,int color,String username){}
    DiscordBridge(JavaPlugin plugin){this.plugin=plugin;}

    void start(){
        var c=plugin.getConfig();
        if(!c.getBoolean("discord.enabled",false))return;
        webhook=c.getString("discord.webhook-url","").trim();
        serverName=c.getString("discord.server-name","Server");
        avatar=c.getString("discord.avatar-url","").trim();
        footer=c.getString("discord.footer","AnarchyCore Discord");
        color=c.getInt("discord.embed-color",0x5865F2);
        if(webhook.isBlank()){plugin.getLogger().warning("Discord enabled but webhook-url is empty.");return;}
        running=true;
        exec=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AnarchyCore-Discord");t.setDaemon(true);return t;});
        exec.execute(this::sendLoop);
        event("Server online","AnarchyCore is online.",0x57F287);
    }
    void stop(String status){if(status!=null)event("Server offline",status,0xED4245);running=false;if(exec!=null)exec.shutdownNow();}
    void event(String message){event("Server event",message,color);}
    void event(String title,String message,int c){if(!running)return;queue.offer(new Payload("",title,message,c,serverName));}
    void chat(String player,String message){if(!running)return;queue.offer(new Payload("",null,"**"+safe(player)+"**: "+safe(message),0x5865F2,serverName));}
    void player(String title,String player,int c){event(title,"`"+safe(player)+"`",c);}
    private void sendLoop(){
        while(running){
            try{Payload p=queue.poll(1,TimeUnit.SECONDS);if(p==null)continue;
                JsonObject body=new JsonObject();body.addProperty("username",p.username());
                if(!avatar.isBlank())body.addProperty("avatar_url",avatar);
                if(!p.content().isBlank())body.addProperty("content",p.content());
                JsonArray embeds=new JsonArray();JsonObject e=new JsonObject();
                if(p.title()!=null&&!p.title().isBlank())e.addProperty("title",p.title());
                e.addProperty("description",limit(p.description(),3900));e.addProperty("color",p.color());
                JsonObject f=new JsonObject();f.addProperty("text",footer);e.add("footer",f);embeds.add(e);body.add("embeds",embeds);
                HttpRequest req=HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                HttpResponse<Void> res=http.send(req,HttpResponse.BodyHandlers.discarding());
                if(res.statusCode()>=300)plugin.getLogger().warning("Discord webhook returned HTTP "+res.statusCode());
            }catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){plugin.getLogger().warning("Discord bridge error: "+e.getMessage());}
        }
    }
    private static String safe(String s){return s==null?"":s.replace("@everyone","@​everyone").replace("@here","@​here");}
    private static String limit(String s,int n){return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
