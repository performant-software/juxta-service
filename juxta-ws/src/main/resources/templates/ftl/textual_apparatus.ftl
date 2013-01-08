<!DOCTYPE html>
<html>
    <head>
        <title>${title}</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <style>
            body { 
               margin: 20px;
               font-family: "Times New Roman", Times, serif
            }
            ul {
               list-style: none;
            }
            .num-col {
               width: 60px;
               vertical-align: top;
               color: #BBB;
               font-weight: 100;
               font-size: 0.8em;
               text-align: right;
               padding-right: 10px;
            }
         </style>
    </head>

    <body>
       <div id="base-text">
          <table>
            <@fileReader src="${baseWitnessText}"/>   
          </table>
       </div>
       
       <div id="textual-notes">
          <h4>Textual Notes</h4>
          <ul>
             <#list witnesses as witness>
                <li>${witness.siglum}: ${witness.title}<#if witness.isBase> - This is the base text.</#if></li>
             </#list>
          </ul>
       </div>
       
       <div id="apparatus">
          <table>
            <@fileReader src="${apparatusFile}"/>    
          </table>
       </div>
    </body>
</html>