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

    const res = await fetch("/assign", 
    { 
        method:"POST", 
        headers: 
        { 
        "Content-Type": 
        "application/x-www-form-urlencoded" 
        }, 
        body:params 
    } );
    const data = await res.text();

    const data = await res.jason();
    updateButton(date,time,person,data);

} 

window.onload = function() 
{ 
    var tabId = localStorage.getItem("activeTab"); 
    var buttonId = localStorage.getItem("activeButton"); 
    if(tabId && buttonId){openTab(tabId,buttonId);} 
} 

document.addEventListener("DOMContentLoaded", () =>
{
    document.querySelectorAll("#assignArea button").forEach(btn =>
    {
        btn.addEventListener("click",async () =>
        {
           const date = btn.date.dataset.date;
           const time = btn.dataset.time;
           const person = btn.dataset.person;
           
           await assignedPerson(date,time,person);
        });
    });
});

function updateButton(date,time,person,data)
{
    const buttons = document.querySelectorAll("button");
    buttons.forEach(btn =>
    {
        if(btn.dataset.date === date && btn.dataset.time === time && btn.dataset.person === person)
        {
            if(data.selected){btn.style.background = "#808080";}
            else{btn.style.background = "#FFFFFF";}
        }
    });
}