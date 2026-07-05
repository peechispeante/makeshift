function togglePreview()
{
    const drawer = document.getElementById("previewDrawer");
    const button = document.getElementById("previewButton");

    drawer.classList.toggle("open");

    if(drawer.classList.contains("open")){button.textContent = ">>";}
    else{button.textContent = "<<";}
}

function openTab(tabId,buttonId){ 
    console.log(tabId); 
    console.log(buttonId); 
    
    var tabs=document.getElementsByClassName("tabcontent"); 
    for(var i=0;i<tabs.length;i++){  tabs[i].style.display="none";}  
    
    var buttons = document.getElementsByClassName("tabButton"); 
    for(var i=0;i<buttons.length;i++){buttons[i].style.background="#f0f0f0";} 

    document.getElementById(tabId).style.display="block";  
    document.getElementById(buttonId).style.background="#808080";     
    localStorage.setItem("activeTab",tabId); 
    localStorage.setItem("activeButton",buttonId); 
} 

async function assignedPerson(date,time,person){ 
    const params =  
    "date="+encodeURIComponent(date) 
    "&time="+encodeURIComponent(time) 
    "&person="+encodeURIComponent(person); 

    await fetch("/assign", 
    { 
        method:"POST", 
        headers: 
        { 
        "Content-Type": 
        "application/x-www-form-urlencoded" 
        }, 
        body:params 
    } );
    
    location.reload(); 
} 

window.onload = function() 
{ 
    var tabId = localStorage.getItem("activeTab"); 
    var buttonId = localStorage.getItem("activeButton"); 
    if(tabId && buttonId){openTab(tabId,buttonId);} 
} 