<!DOCTYPE html>
<html>
    <head>
        <title>${title}</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <style>
            
            ul {
               list-style: none;
            }
            .num-col {
               width: 65px;
               vertical-align: top;
               color: #BBB;
               font-weight: 100;
               font-size: 0.8em;
               text-align: right;
               padding-right: 10px;
               height: 17px;
            }
         </style>
    </head>

    <body>
      <h3>${title}</h3>
       
       <table style="border-top: none; border-top-style: none;">
         <@fileReader src="${baseWitnessText}"/>   
       </table>
       
       <h4>Textual Notes</h4>
       <ul>
          <#list witnesses as witness>
             <#if witness.isIncluded>
                <li><b>${witness.siglum}: </b>${witness.title}<#if witness.isBase> - This is the base text.</#if></li>
             </#if>
          </#list>
       </ul>
       
       <table style="border-top: none; border-top-style: none;">
         <@fileReader src="${apparatusFile}"/>    
       </table>
    </body>
</html>