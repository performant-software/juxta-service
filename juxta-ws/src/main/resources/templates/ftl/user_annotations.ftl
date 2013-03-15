<#list annotations as anno>
   <div class="ua" juxta:range="${anno.baseRange.start},${anno.baseRange.end}">
      <p class="ua">${anno.baseFragment}</p><div class="show-ua"></div>
      <div style="clear:both"></div>
      <table class="ua">
         <tr class="ua-header"><th>Witness</th><th>Annotation</th></tr>
         <#list anno.notes as note>
            <tr class="ua-data"><td>${note.witnessName}</td><td>${note.text}</td></tr>
         </#list>
      </table>
   </div>
</#list>
