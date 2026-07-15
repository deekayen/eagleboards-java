package shkc.json.simple.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import shkc.json.simple.JSONArray;
import shkc.json.simple.JSONObject;

public class JSONParser {
   public static final int S_INIT = 0;
   public static final int S_IN_FINISHED_VALUE = 1;
   public static final int S_IN_OBJECT = 2;
   public static final int S_IN_ARRAY = 3;
   public static final int S_PASSED_PAIR_KEY = 4;
   public static final int S_IN_PAIR_VALUE = 5;
   public static final int S_END = 6;
   public static final int S_IN_ERROR = -1;
   private LinkedList handlerStatusStack;
   private Yylex lexer = new Yylex((Reader)null);
   private Yytoken token = null;
   private int status = 0;

   private int peekStatus(LinkedList var1) {
      if (var1.size() == 0) {
         return -1;
      }

      Integer var2 = (Integer)var1.getFirst();
      return var2;
   }

   public void reset() {
      this.token = null;
      this.status = 0;
      this.handlerStatusStack = null;
   }

   public void reset(Reader var1) {
      this.lexer.yyreset(var1);
      this.reset();
   }

   public int getPosition() {
      return this.lexer.getPosition();
   }

   public Object parse(String var1) throws ParseException {
      return this.parse(var1, (ContainerFactory)null);
   }

   public Object parse(String var1, ContainerFactory var2) throws ParseException {
      StringReader var3 = new StringReader(var1);

      try {
         return this.parse(var3, var2);
      } catch (IOException var5) {
         throw new ParseException(-1, 2, var5);
      }
   }

   public Object parse(Reader var1) throws IOException, ParseException {
      return this.parse(var1, (ContainerFactory)null);
   }

   public Object parse(Reader var1, ContainerFactory var2) throws IOException, ParseException {
      this.reset(var1);
      LinkedList var3 = new LinkedList();
      LinkedList var4 = new LinkedList();

      try {
         do {
            this.nextToken();
            label64:
            switch (this.status) {
               case -1:
                  throw new ParseException(this.getPosition(), 1, this.token);
               case 0:
                  switch (this.token.type) {
                     case 0:
                        this.status = 1;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(this.token.value);
                        break label64;
                     case 1:
                        this.status = 2;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(this.createObjectContainer(var2));
                        break label64;
                     case 2:
                     default:
                        this.status = -1;
                        break label64;
                     case 3:
                        this.status = 3;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(this.createArrayContainer(var2));
                        break label64;
                  }
               case 1:
                  if (this.token.type == -1) {
                     return var4.removeFirst();
                  }

                  throw new ParseException(this.getPosition(), 1, this.token);
               case 2:
                  switch (this.token.type) {
                     case 0:
                        if (this.token.value instanceof String) {
                           String var15 = (String)this.token.value;
                           var4.addFirst(var15);
                           this.status = 4;
                           var3.addFirst(new Integer(this.status));
                        } else {
                           this.status = -1;
                        }
                        break label64;
                     case 2:
                        if (var4.size() > 1) {
                           var3.removeFirst();
                           var4.removeFirst();
                           this.status = this.peekStatus(var3);
                        } else {
                           this.status = 1;
                        }
                     case 5:
                        break label64;
                     default:
                        this.status = -1;
                        break label64;
                  }
               case 3:
                  switch (this.token.type) {
                     case 0:
                        List var14 = (List)var4.getFirst();
                        var14.add(this.token.value);
                        break label64;
                     case 1:
                        List var13 = (List)var4.getFirst();
                        Map var18 = this.createObjectContainer(var2);
                        var13.add(var18);
                        this.status = 2;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(var18);
                        break label64;
                     case 2:
                     default:
                        this.status = -1;
                        break label64;
                     case 3:
                        List var12 = (List)var4.getFirst();
                        List var19 = this.createArrayContainer(var2);
                        var12.add(var19);
                        this.status = 3;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(var19);
                        break label64;
                     case 4:
                        if (var4.size() > 1) {
                           var3.removeFirst();
                           var4.removeFirst();
                           this.status = this.peekStatus(var3);
                        } else {
                           this.status = 1;
                        }
                     case 5:
                        break label64;
                  }
               case 4:
                  switch (this.token.type) {
                     case 0:
                        var3.removeFirst();
                        String var11 = (String)var4.removeFirst();
                        Map var17 = (Map)var4.getFirst();
                        var17.put(var11, this.token.value);
                        this.status = this.peekStatus(var3);
                        break;
                     case 1:
                        var3.removeFirst();
                        String var10 = (String)var4.removeFirst();
                        Map var16 = (Map)var4.getFirst();
                        Map var8 = this.createObjectContainer(var2);
                        var16.put(var10, var8);
                        this.status = 2;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(var8);
                        break;
                     case 2:
                     case 4:
                     case 5:
                     default:
                        this.status = -1;
                        break;
                     case 3:
                        var3.removeFirst();
                        String var5 = (String)var4.removeFirst();
                        Map var6 = (Map)var4.getFirst();
                        List var7 = this.createArrayContainer(var2);
                        var6.put(var5, var7);
                        this.status = 3;
                        var3.addFirst(new Integer(this.status));
                        var4.addFirst(var7);
                     case 6:
                  }
            }

            if (this.status == -1) {
               throw new ParseException(this.getPosition(), 1, this.token);
            }
         } while (this.token.type != -1);
      } catch (IOException var9) {
         throw var9;
      }

      throw new ParseException(this.getPosition(), 1, this.token);
   }

   private void nextToken() throws ParseException, IOException {
      this.token = this.lexer.yylex();
      if (this.token == null) {
         this.token = new Yytoken(-1, null);
      }
   }

   private Map createObjectContainer(ContainerFactory var1) {
      if (var1 == null) {
         return new JSONObject();
      }

      Map var2 = var1.createObjectContainer();
      return var2 == null ? new JSONObject() : var2;
   }

   private List createArrayContainer(ContainerFactory var1) {
      if (var1 == null) {
         return new JSONArray();
      }

      List var2 = var1.creatArrayContainer();
      return var2 == null ? new JSONArray() : var2;
   }

   public void parse(String var1, ContentHandler var2) throws ParseException {
      this.parse(var1, var2, false);
   }

   public void parse(String var1, ContentHandler var2, boolean var3) throws ParseException {
      StringReader var4 = new StringReader(var1);

      try {
         this.parse(var4, var2, var3);
      } catch (IOException var6) {
         throw new ParseException(-1, 2, var6);
      }
   }

   public void parse(Reader var1, ContentHandler var2) throws IOException, ParseException {
      this.parse(var1, var2, false);
   }

   public void parse(Reader var1, ContentHandler var2, boolean var3) throws IOException, ParseException {
      if (!var3) {
         this.reset(var1);
         this.handlerStatusStack = new LinkedList();
      } else if (this.handlerStatusStack == null) {
         var3 = false;
         this.reset(var1);
         this.handlerStatusStack = new LinkedList();
      }

      LinkedList var4 = this.handlerStatusStack;

      try {
         do {
            label172:
            switch (this.status) {
               case -1:
                  throw new ParseException(this.getPosition(), 1, this.token);
               case 0:
                  var2.startJSON();
                  this.nextToken();
                  switch (this.token.type) {
                     case 0:
                        this.status = 1;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.primitive(this.token.value)) {
                           return;
                        }
                        break label172;
                     case 1:
                        this.status = 2;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startObject()) {
                           return;
                        }
                        break label172;
                     case 2:
                     default:
                        this.status = -1;
                        break label172;
                     case 3:
                        this.status = 3;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startArray()) {
                           return;
                        }
                        break label172;
                  }
               case 1:
                  this.nextToken();
                  if (this.token.type == -1) {
                     var2.endJSON();
                     this.status = 6;
                     return;
                  }

                  this.status = -1;
                  throw new ParseException(this.getPosition(), 1, this.token);
               case 2:
                  this.nextToken();
                  switch (this.token.type) {
                     case 0:
                        if (this.token.value instanceof String) {
                           String var5 = (String)this.token.value;
                           this.status = 4;
                           var4.addFirst(new Integer(this.status));
                           if (!var2.startObjectEntry(var5)) {
                              return;
                           }
                        } else {
                           this.status = -1;
                        }
                        break label172;
                     case 2:
                        if (var4.size() > 1) {
                           var4.removeFirst();
                           this.status = this.peekStatus(var4);
                        } else {
                           this.status = 1;
                        }

                        if (!var2.endObject()) {
                           return;
                        }
                     case 5:
                        break label172;
                     default:
                        this.status = -1;
                        break label172;
                  }
               case 3:
                  this.nextToken();
                  switch (this.token.type) {
                     case 0:
                        if (!var2.primitive(this.token.value)) {
                           return;
                        }
                        break label172;
                     case 1:
                        this.status = 2;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startObject()) {
                           return;
                        }
                        break label172;
                     case 2:
                     default:
                        this.status = -1;
                        break label172;
                     case 3:
                        this.status = 3;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startArray()) {
                           return;
                        }
                        break label172;
                     case 4:
                        if (var4.size() > 1) {
                           var4.removeFirst();
                           this.status = this.peekStatus(var4);
                        } else {
                           this.status = 1;
                        }

                        if (!var2.endArray()) {
                           return;
                        }
                     case 5:
                        break label172;
                  }
               case 4:
                  this.nextToken();
                  switch (this.token.type) {
                     case 0:
                        var4.removeFirst();
                        this.status = this.peekStatus(var4);
                        if (!var2.primitive(this.token.value)) {
                           return;
                        }

                        if (!var2.endObjectEntry()) {
                           return;
                        }
                        break label172;
                     case 1:
                        var4.removeFirst();
                        var4.addFirst(new Integer(5));
                        this.status = 2;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startObject()) {
                           return;
                        }
                        break label172;
                     case 2:
                     case 4:
                     case 5:
                     default:
                        this.status = -1;
                        break label172;
                     case 3:
                        var4.removeFirst();
                        var4.addFirst(new Integer(5));
                        this.status = 3;
                        var4.addFirst(new Integer(this.status));
                        if (!var2.startArray()) {
                           return;
                        }
                     case 6:
                        break label172;
                  }
               case 5:
                  var4.removeFirst();
                  this.status = this.peekStatus(var4);
                  if (!var2.endObjectEntry()) {
                     return;
                  }
                  break;
               case 6:
                  return;
            }

            if (this.status == -1) {
               throw new ParseException(this.getPosition(), 1, this.token);
            }
         } while (this.token.type != -1);
      } catch (IOException var6) {
         this.status = -1;
         throw var6;
      } catch (ParseException var7) {
         this.status = -1;
         throw var7;
      } catch (RuntimeException var8) {
         this.status = -1;
         throw var8;
      } catch (Error var9) {
         this.status = -1;
         throw var9;
      }

      this.status = -1;
      throw new ParseException(this.getPosition(), 1, this.token);
   }
}
