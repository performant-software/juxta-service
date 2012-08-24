<!-- SELECT Workspace popup -->
<div id="select-ws" class="workspace-popup">
    <div class="popup-title">Select Workspace</div>
    <select id="workspace-select" class="workspace-select" size="${workspaceCount}">
        <#list workspaces as ws> 
            <option id="${ws.id}" class="workspace-option">${ws.name}</option>
        </#list>
    </select>
    <div class="overlay-buttons">
        <a id="new-workspace" class="juxta-button" style="float:left">New</a>
        <a id="cancel-workspace" class="juxta-button">Cancel</a>
        <a id="ok-workspace" class="juxta-button">OK</a>
    </div>
</div>


<!-- CREATE workspace popup -->
<div id="create-ws" class="workspace-popup">
    <div class="popup-title">Create Workspace</div>
    <table class="ws-create">
        <tr>
            <td class="ws-create" style="text-align: right">Name:</td>
            <td class="ws-create" ><input id="ws-name" type="text" style="width:100%"/></td>
         </tr>
        <tr>
            <td class="ws-create" style="text-align: right">Description:</td>
            <td class="ws-create"><textarea id="ws-desc"  style="width:100%" rows="3"></textarea></td>
        </tr>
    </table>
    
    <div class="overlay-buttons">
        <a id="cancel-create" class="juxta-button">Cancel</a>
        <a id="ok-create" class="juxta-button">OK</a>
    </div>
</div>
