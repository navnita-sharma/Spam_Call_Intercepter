# Grandparent Guardian: Scam Detection Test Suite

This document contains 50+ test scripts to evaluate the `NLPScamDetector`. 
Read these phrases aloud into the app or pass them directly to the STT engine to verify detection.

## Level 1: EASY (Direct Keyword Hits)
These phrases contain obvious, blacklisted scam words. They should trigger the **Keyword Match** layer instantly.

1. "Hello, I am calling from the **IRS**. You owe back taxes."
2. "To fix your computer, please download **AnyDesk** right now."
3. "We need to verify your identity. Please tell me your **OTP**."
4. "Your account is locked. Pay the **penalty** using **Bitcoin**."
5. "Go to the store and buy a $500 Apple **gift card**."
6. "You have an **unauthorized** charge on your **Amazon** account."
7. "This is the **police**. There is a warrant for your **arrest**."
8. "I am your **bank manager**. Your account is compromised."
9. "Congratulations! You won the international **lottery**."
10. "Give me **remote access** to your laptop to remove the virus."
11. "We noticed suspicious activity. We need to **verify account** details."
12. "This is a very **urgent** matter regarding your credit card."
13. "Please install **TeamViewer** so our tech support can help."
14. "Send the payment via **crypto** immediately."
15. "I'm calling from **Walmart** about a pending **refund**."
16. "Your **Social Security** number has been suspended."
17. "Do this **immediately** or you will lose all your money."

## Level 2: MEDIUM (Semantic Patterns)
These scripts might not use the exact single blacklisted words in an obvious way, or they combine multiple concepts. They should trigger the **Pattern Match (Semantic)** layer.

18. "Listen to me very carefully, your money is not safe. You must act right now to secure it." (Urgency + Account)
19. "We are dispatching officers to your location unless you pay the fine." (Implicit police/arrest threat)
20. "Just read me the six-digit number that was just sent to your phone." (Implicit OTP/PIN request)
21. "Your computer is sending hackers our way. Type 'www dot support dot com' into your browser."
22. "You are the lucky selected winner of our grand prize, we just need a small processing fee."
23. "I am a federal agent. We found a package with your name on it containing illegal items."
24. "Your grandson is in jail and needs bail money wired to this account fast." (Grandparent Scam)
25. "There's a problem with your tax return. We need your banking routing number to fix it."
26. "To process your refund, I need you to log into your banking app while we are on the phone."
27. "Your electricity will be shut off in thirty minutes unless you pay over the phone."
28. "This is fraud prevention. Did you authorize a $1000 charge in Ohio? No? Then give me your card number to cancel it."
29. "I accidentally sent you too much money on Zelle. Please send the extra $500 back."
30. "We are upgrading our security systems, and we need your current password to migrate your profile."
31. "You are eligible for government loan forgiveness, just pay the upfront application cost."
32. "If you don't comply, a lawsuit will be filed against you by the end of the day."
33. "Please share your screen with me so I can show you how to get your money back."

## Level 3: HARD (Conversational & Evasion)
These scripts are designed to trick the system by burying the intent in casual conversation, or they test the system's ability to minimize false positives.

**Hard Scams (Should Flag):**
34. "Hi there, how is your day going? I'm just calling because there was a tiny glitch with your recent order, and we just need you to confirm your billing zip code and the three numbers on the back of your card."
35. "Grandma, it's me. I was in a car accident and I broke my nose so I sound different. Please don't tell mom, just wire $2000 to my lawyer so I can get out of the station."
36. "Hey, it's John from IT. We're doing a routine check. I sent a push notification to your phone, just tap 'Approve' for me."
37. "We noticed a login from Russia on your profile. To prove it wasn't you, read the security text message back to me."
38. "Our records show you overpaid on your utility bill last month. We want to deposit the difference directly, what is your routing number?"
39. "I'm looking at your device diagnostics and it's heavily infected. If you open your start menu, I can guide you on how to let me in to fix it."
40. "I know this sounds scary, but you are part of an ongoing money laundering investigation. You must move your funds to the secure government locker."

**Safe Conversations (Must NOT Flag - False Positive Test):**
41. "Hey, could you stop by Walmart and pick up some milk and eggs?" (Uses "Walmart")
42. "I need to call the police because my car was just broken into." (Uses "police")
43. "Did you see the news about the guy who won the lottery in California?" (Uses "lottery")
44. "I have to log into my bank account to pay the electric bill." (Uses "bank account")
45. "I sent you an Amazon gift card for your birthday, hope you like it!" (Uses "Amazon", "gift card")
46. "I need to get a refund for these shoes, they don't fit right." (Uses "refund")
47. "Can you come over immediately? The pipe in the kitchen just burst!" (Uses "immediately")
48. "My social security check arrived late this month." (Uses "social security")
49. "Did you manage to fix the remote access on your work profile?" (Uses "remote access")
50. "I lost my debit card so I had to order a new pin number." (Uses "pin")
51. "It's urgent that we finish this project before the deadline." (Uses "urgent")
52. "He got arrested for speeding on the highway." (Uses "arrested")
