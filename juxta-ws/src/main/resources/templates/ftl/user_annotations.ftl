<#list annotations as anno>
   <div class="ua">
      <p class="ua">${anno.baseFragment}</p>
      <table class="ua">
         <tr class="ua-header"><th>Witness</th><th>Annotation</th></tr>
         <#list anno.notes as note>
            <tr class="ua-data"><td>${note.witnessName}</td><td>${note.note}</td></tr>
         </#list>
      </table>
   </div>
</#list>
