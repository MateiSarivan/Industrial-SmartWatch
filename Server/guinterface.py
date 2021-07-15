#listbox scrollbar
from random import randint
import tkinter as tk
import threading
import smartwatch_server
import time
from threading import Lock

lock = Lock()

trigger = False
root = tk.Tk()
root.title("Task Deployer")
root.geometry("400x400")

switch = 1
def scrolllistbox(event, lb):
	global switch
	if switch==1:
		lb.yview_scroll(int(-4*(event.delta/120)), "units")
		print(event)
 
def do_switch():
	global switch
	if switch:
		switch = 0
		label['text'] = "Not in sync"
	else:
		switch = 1
		label['text'] = "In sync"
 
 
def def_listbox():
	scrollbar1 = tk.Scrollbar(frame1)
	scrollbar1.pack(side=tk.RIGHT, fill=tk.Y)
	listbox1 = tk.Listbox(frame1)
	scrollbar1.config(command=listbox1.yview)
	listbox1.pack(expand=1, fill="both", side="right")
	listbox1.config(yscrollcommand=scrollbar1.set)
	return listbox1

 
# ====================== LISTBOXES =======================================
frame1 = tk.Frame(root)
frame1.pack(expand=1, fill="both")
listbox1 = def_listbox()
listbox2 = def_listbox()



p = smartwatch_server.SmartWatchServer()
listbox1.bind("<MouseWheel>", lambda event: scrolllistbox(event, listbox2))
listbox2.bind("<MouseWheel>", lambda event: scrolllistbox(event, listbox1))
# ================== SWITCH BUTTON =========================
frame2 = tk.Frame(root)
frame2.pack()


def this_this():
    global trigger, lock
    current_no_tasks = 0
    current_no_devices = 0
    while True:
        time.sleep(0.05)
        lock.acquire()
        cond = trigger
        lock.release()
        if cond:
            break
        else:
            devices = p.get_devices()
            if len(devices) != current_no_devices:
                current_no_devices = len(devices)
                listbox1.delete(0, listbox1.size()-1)
                for device in devices:
                    listbox1.insert(tk.END, device.get_id())

            tasks = p.get_tasks()
            if len(tasks) != current_no_tasks:
                current_no_tasks = len(tasks)
                listbox2.delete(0, listbox2.size()-1)
                for task in tasks:
                    listbox2.insert(tk.END, task.get_text())

            if len(tasks) == current_no_tasks:
                i = 0
                for task in tasks:
                    if "Acquired" in task.get_status():
                        listbox2.itemconfig(i, bg='cyan')
                    else:
                        listbox2.itemconfig(i, bg='white')
                    i += 1
            
            
        
a_thread = threading.Thread(target=this_this)
a_thread.start()
option = "NORMAL"
def opt_select(selection):
    global option
    option = selection

text_1 = tk.Text(frame2, height=1, width=30)

text_2 = tk.Text(frame2, height=1, width=30)
var = tk.StringVar(root)
var.set("NORMAL")
text_3 = tk.OptionMenu(frame2, var, "NORMAL", "URGENT", command=opt_select)


def do_this():
    global option
    origin = text_1.get("1.0", tk.END).strip('\n')
    description = text_2.get("1.0", tk.END).strip('\n')
    urgency = option
    task_id = p.new_task(origin, description, urgency)
    text_1.delete(1.0,"end")
    text_2.delete(1.0,"end")
    var.set("NORMAL")
    option = "normal"
label = tk.Label(frame2, text = "Task Origin")
button = tk.Button(frame2, text= "Add Task", command=do_this)
label2 = tk.Label(frame2, text = "Task Description")
label3 = tk.Label(frame2, text = "Task Urgency")
label.pack()
text_1.pack()
label2.pack()
text_2.pack()


label3.pack()
text_3.pack()
button.pack()


def on_closing():
    global trigger, lock
    # if messagebox.askokcancel("Quit", "Do you want to quit?"):
    #     root.destroy()
    
    p.disconnect_devices()
    p.close_communication()
    print("attempt close")
    lock.acquire()
    trigger = True
    lock.release()
    time.sleep(0.2)
    root.destroy()



root.protocol("WM_DELETE_WINDOW", on_closing)

# ==========================================================

root.mainloop()
