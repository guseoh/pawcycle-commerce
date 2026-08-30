"""Static review artwork only. No web/app implementation. Run with bundled Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'visuals'
INK='#241C2E'; MUTED='#68616F'; BRAND='#4B286D'; HOVER='#3C1F58'
SOFT='#F3F0F7'; LINE='#DED8E4'; EDGE='#887D92'; APRICOT='#F3B88F'
ERROR='#B42336'; SALE='#955000'; SUCCESS='#236447'
FONT='C:/Windows/Fonts/malgun.ttf'; BOLD='C:/Windows/Fonts/malgunbd.ttf'
sheet=Image.open(ROOT/'assets/packaging-concept-r1.png').convert('RGB')
half=sheet.width//2
products=[sheet.crop((i%2*half,i//2*half,(i%2+1)*half,(i//2+1)*half)) for i in range(4)]
names=['데일리 사료','동결건조 간식','고양이 모래','데일리 샴푸']
opts=['2.5kg · 성견용','80g · 닭고기','4kg · 무향','300ml']
prices=['32,000원','12,000원','24,000원','18,000원']

class Board:
    def __init__(self,w,h):
        self.w=w;self.h=h;self.m=w<600;self.g=16 if self.m else 80
        self.im=Image.new('RGB',(w,h),'white');self.d=ImageDraw.Draw(self.im)
    def rect(self,x,y,w,h,fill='white',r=0,border=None,sw=1):
        self.d.rounded_rectangle((x,y,x+w,y+h),radius=r,fill=fill,outline=border,width=sw)
    def line(self,x,y,x2,y2,c=LINE,sw=1): self.d.line((x,y,x2,y2),fill=c,width=sw)
    def text(self,x,y,t,s=16,c=INK,b=False):
        self.d.text((x,y),t,font=ImageFont.truetype(BOLD if b else FONT,s),fill=c)
    def wrap(self,x,y,t,w,s=16,c=INK,b=False,lh=None):
        f=ImageFont.truetype(BOLD if b else FONT,s);lines=[];cur=''
        for word in t.split(' '):
            n=(cur+' '+word).strip()
            if self.d.textlength(n,font=f)>w and cur:lines.append(cur);cur=word
            else:cur=n
        if cur:lines.append(cur)
        for i,v in enumerate(lines):self.text(x,y+i*(lh or s*1.55),v,s,c,b)
        return len(lines)*(lh or s*1.55)
    def right(self,x,y,t,s=16,c=INK,b=False):
        f=ImageFont.truetype(BOLD if b else FONT,s);self.text(x-self.d.textlength(t,font=f),y,t,s,c,b)
    def orbit(self,x,y,size,c=BRAND):
        for dx in (0,size*.36):self.d.ellipse((x+dx,y,x+dx+size*.64,y+size),outline=c,width=max(2,int(size*.035)))
    def button(self,x,y,w,t,kind='primary',h=48):
        arrow='⌄' in t;t=t.replace('⌄','').replace('−','-').rstrip()
        fill=BRAND if kind=='primary' else 'white';color='white' if kind=='primary' else BRAND
        if kind=='disabled':fill='#E9E5ED';color=MUTED
        self.rect(x,y,w,h,fill,8,None if kind in ('primary','disabled') else EDGE)
        f=ImageFont.truetype(BOLD,14 if w<130 else 16);tw=self.d.textlength(t,font=f)
        self.text(x+(w-tw)/2,y+(h-f.size)/2-3,t,f.size,color,True)
        if arrow:self.d.line((x+w-23,y+h/2-3,x+w-18,y+h/2+2,x+w-13,y+h/2-3),fill=color,width=2)
    def chip(self,x,y,w,t,selected=False):
        self.rect(x,y,w,40,SOFT if selected else 'white',20,BRAND if selected else LINE)
        self.text(x+14,y+9,t,14,BRAND if selected else MUTED,selected)
    def check(self,x,y,selected=False):
        self.rect(x,y,18,18,BRAND if selected else 'white',4,BRAND if selected else EDGE)
        if selected:self.d.line((x+4,y+9,x+8,y+13,x+14,y+5),fill='white',width=2)
    def radio(self,x,y,on=False):
        self.d.ellipse((x,y,x+18,y+18),outline=BRAND if on else EDGE,width=2)
        if on:self.d.ellipse((x+5,y+5,x+13,y+13),fill=BRAND)
    def photo(self,x,y,w,h,i):
        # Contact sheet quadrant is placed in the layout; source artwork is preserved.
        p=products[i%4].copy();p.thumbnail((int(w),int(h)),Image.Resampling.LANCZOS)
        self.rect(x,y,w,h,SOFT,8);self.im.paste(p,(int(x+(w-p.width)/2),int(y+(h-p.height)/2)))
    def wordmark(self,x,y,small=False,c=BRAND):
        sz=24 if small else 32;self.orbit(x,y+3,sz,c);self.text(x+sz+9,y-1,'PawCycle',sz,c,True)
    def header(self,compact=False):
        g=self.g
        if self.m:
            self.wordmark(g,15,True);self.right(self.w-g,22,'상품 보기' if compact else '메뉴  장바구니',13,BRAND,True)
            self.line(0,63,self.w,63)
            if not compact:
                self.rect(g,76,self.w-2*g,48,SOFT,8);self.text(g+14,90,'필요한 물품을 검색하세요',14,MUTED);self.right(self.w-g-12,90,'검색',14,BRAND,True)
            return 64 if compact else 136
        self.wordmark(g,22)
        if not compact:
            self.text(320,34,'상품',15,BRAND,True);self.text(372,34,'카테고리',15)
            self.rect(480,20,540,48,SOFT,8);self.text(498,33,'상품명이나 필요한 물품을 검색하세요',15,MUTED);self.text(966,33,'검색',15,BRAND,True)
            self.right(self.w-g,35,'내 정보    찜    장바구니',14)
        else:self.right(self.w-g,34,'상품으로 돌아가기',14,BRAND)
        self.line(0,87,self.w,87);return 88
    def title(self,y,title,sub=None):
        self.text(self.g,y,title,28 if self.m else 36,INK,True)
        if sub:self.text(self.g,y+(46 if self.m else 58),sub,14,MUTED)
    def footer(self,y,compact=False):
        self.rect(0,y,self.w,self.h-y,SOFT)
        if compact:
            self.wordmark(self.g,y+28,True);self.text(self.g,y+81,'고객지원    배송 안내    반품 안내',14,BRAND)
        elif self.m:
            self.wordmark(16,y+25,True);self.text(16,y+77,'고객지원    FAQ    배송·반품',14,BRAND)
            self.text(16,y+116,'상품   주문   정기배송   내 정보',13,MUTED)
        else:
            self.wordmark(80,y+34);self.text(80,y+83,'함께 사는 일상, 필요한 만큼.',16,MUTED)
            for x,t,v in [(620,'쇼핑','상품  ·  정기배송'),(855,'내 일상','주문  ·  내 정보'),(1090,'도움과 안내','고객지원  ·  FAQ')]:
                self.text(x,y+38,t,15,BRAND,True);self.text(x,y+78,v,14,MUTED)
            self.text(1090,y+109,'배송  ·  반품  ·  공지',14,MUTED)
        self.text(self.g,self.h-28,'R1 디자인 제안 · 가상 상품/금액 · 미승인',11,MUTED)
    def field(self,x,y,w,label,value='',suffix=''):
        self.text(x,y,label,14,INK,True);self.rect(x,y+30,w,52,'white',8,EDGE)
        self.text(x+14,y+45,value or label+' 입력',16,INK if value else MUTED)
        if suffix=='⌄':self.d.line((x+w-26,y+51,x+w-20,y+57,x+w-14,y+51),fill=BRAND,width=2)
        elif suffix:self.right(x+w-14,y+45,suffix,14,BRAND)
    def product(self,x,y,w,i,compare=False):
        self.photo(x,y,w,w,i);self.rect(x+12,y+12,44,24,'white',4);self.text(x+19,y+15,'시안',11,MUTED)
        self.rect(x+w-40,y+10,30,30,'white',15);self.text(x+w-33,y+12,'♡',20,BRAND)
        yy=y+w+14;self.text(x,yy,'PAWCYCLE · CONCEPT',11,MUTED)
        self.text(x,yy+24,names[i%4],15 if self.m else 18,INK,True)
        self.text(x,yy+51,opts[i%4],13,MUTED)
        self.text(x,yy+79,prices[i%4],20 if self.m else 24,INK,True)
        self.text(x,yy+113,'정기배송 대상' if i%4 in (0,2) else '일반 구매',12,BRAND)
        if compare:self.check(x,yy+145);self.text(x+27,yy+143,'비교',13,MUTED)
    def summary(self,x,y,w,checkout=False):
        self.rect(x,y,w,354,SOFT,12);self.text(x+24,y+24,'결제 예상 금액',20,INK,True)
        for n,(label,value) in enumerate([('상품 금액','44,000원'),('상품 할인','0원'),('배송비','3,000원')]):
            self.text(x+24,y+73+n*34,label,14,MUTED);self.right(x+w-24,y+73+n*34,value,16)
        self.line(x+24,y+182,x+w-24,y+182)
        self.text(x+24,y+203,'전체 금액',14,INK,True);self.right(x+w-24,y+196,'47,000원',28,BRAND,True)
        self.button(x+24,y+250,w-48,'주문 및 결제 준비' if checkout else '전체 상품 주문',h=52)
        self.text(x+24,y+316,'서버 확인 후 최종 금액이 확정됩니다.',12,MUTED)
    def save(self,name):self.im.save(OUT/('r1-'+name+'.png'))

def home(m=False):
    b=Board(375 if m else 1440,1780 if m else 1500);g=b.g;b.header();y=160 if m else 133
    b.title(y,'함께 사는 일상,','필요한 만큼, 편하게 고르세요.');
    cy=y+96
    for x,w,t,on in [(g,70,'전체',True),(g+80,92,'강아지',False),(g+182,92,'고양이',False)]:b.chip(x,cy,w,t,on)
    cats=['사료','간식','위생·배변','목욕·케어']
    for i,t in enumerate(cats):
        x=g+(i%2)*177 if m else g+i*320;yy=cy+60+(i//2)*48 if m else cy+66
        b.text(x,yy,t,15,INK,True);b.right(x+(164 if m else 280),yy,'→',18,BRAND)
        b.line(x,yy+34,x+(164 if m else 280),yy+34)
    py=cy+192 if m else cy+148
    b.text(g,py,'지금 많이 찾는 상품',22 if m else 26,INK,True)
    if not m:b.right(1360,py+7,'전체 상품 보기 →',14,BRAND)
    for i in range(4):b.product(g+(i%2)*177 if m else g+i*326,py+52+(i//2)*340 if m else py+60,166 if m else 302,i)
    yy=py+773 if m else py+530
    b.rect(g,yy,b.w-2*g,164 if m else 190,BRAND,12);b.orbit(b.w-110 if m else 1110,yy+28,76 if m else 130,APRICOT)
    b.text(g+24,yy+22,'PAW / CYCLE',12,APRICOT,True);b.text(g+24,yy+54,'다시 필요한 순간도',19 if m else 30,'white',True)
    b.text(g+24,yy+88 if m else yy+104,'내 주문에서 편하게.',17 if m else 23,'white');b.text(g+24,yy+125 if m else yy+151,'주문 내역 보기 →',14,'white')
    b.footer(1580 if m else 1280);b.save('home-mobile' if m else 'home-desktop')

def plp(m=False):
    b=Board(375 if m else 1440,2140 if m else 1720);g=b.g;b.header();y=164 if m else 132;b.title(y,'전체 상품','상품 8개 · 가상 카탈로그 시안')
    if m:
        b.button(g,259,158,'필터',kind='secondary');b.button(190,259,169,'추천순  ⌄',kind='secondary');b.chip(g,324,102,'전체 상품',True);py=390
    else:
        for i,t in enumerate(['반려동물  ⌄','카테고리  ⌄','브랜드  ⌄','가격  ⌄','구매 조건  ⌄']):b.button(g+i*174,239,158,t,kind='secondary')
        b.button(1184,239,176,'추천순  ⌄',kind='secondary');b.text(g,314,'조건을 고르면 이곳에 선택한 필터가 표시됩니다.',14,MUTED);py=368
    count=8 if not m else 8
    for i in range(count):
        cols=2 if m else 4;w=166 if m else 302;gap=11 if m else 24
        b.product(g+(i%cols)*(w+gap),py+(i//cols)*(360 if m else 500),w,i%4,True)
    yy=1870 if m else 1440;b.button(b.w/2-24,yy,48,'1',h=44)
    b.footer(1950 if m else 1530);b.save('plp-mobile' if m else 'plp-desktop')

def pdp(m=False):
    b=Board(375 if m else 1440,2040 if m else 1510);g=b.g;b.header();b.text(g,147 if m else 118,'상품  /  사료  /  데일리 사료',13,MUTED)
    if m:b.photo(16,185,343,343,0);x=16;y=554;w=343
    else:b.photo(80,180,620,620,0);x=790;y=181;w=570
    b.text(x,y,'PAWCYCLE · CONCEPT',12,BRAND,True);b.text(x,y+32,'데일리 사료',28 if m else 36,INK,True)
    b.text(x,y+82,'2.5kg · 성견용',16,MUTED);b.text(x,y+120,'32,000원',32,INK,True)
    b.text(x,y+172,'정기배송 대상',14,BRAND,True);b.line(x,y+208,x+w,y+208)
    b.text(x,y+230,'용량',14,INK,True);b.button(x,y+262,110,'2.5kg 선택',kind='secondary');b.button(x+122,y+262,110,'5kg 품절',kind='disabled')
    b.text(x,y+333,'수량',14,INK,True);b.button(x,y+365,44,'−',kind='disabled',h=44);b.text(x+67,y+375,'1',18,INK,True);b.button(x+100,y+365,44,'+',kind='secondary',h=44)
    b.button(x,y+438,w-60,'장바구니에 담기',h=52);b.button(x+w-48,y+438,48,'♡',kind='secondary',h=52)
    b.text(x,y+510,'정기배송 플랜 확인 →',15,BRAND,True)
    b.wrap(x,y+546,'배송비와 최종 결제 금액은 주문 단계에서 확인할 수 있어요.',w,14,MUTED)
    yy=1178 if m else 881;b.line(g,yy,b.w-g,yy);b.text(g,yy+25,'상품 정보    리뷰    상품 문의',16,BRAND,True)
    b.text(g,yy+84,'매일의 식사를 위한 구성',23,INK,True)
    b.wrap(g,yy+129,'옵션별 구성과 원재료는 판매자가 제공한 상품 정보를 확인하세요. 이 화면의 사진과 상품명은 디자인 검토를 위한 가상 예시입니다.',b.w-2*g if m else 820,16,MUTED)
    b.text(g,yy+234,'리뷰',22,INK,True);b.text(g,yy+277,'아직 등록된 리뷰가 없어요.',15,MUTED);b.text(g,yy+335,'상품 문의',22,INK,True);b.text(g,yy+378,'궁금한 점을 남겨 주세요.',15,MUTED)
    b.footer(1820 if m else 1300);b.save('pdp-mobile' if m else 'pdp-desktop')

def cart(m=False):
    b=Board(375 if m else 1440,1260 if m else 1020);g=b.g;b.header(True);b.title(104 if m else 130,'장바구니','담은 상품 2개 · 전체 상품을 주문합니다.')
    y=218 if m else 262;w=343 if m else 824
    b.line(g,y,b.w-g if m else g+w,y)
    for i in range(2):
        yy=y+i*186;b.text(g,yy+24,'PAWCYCLE · CONCEPT',11,MUTED);b.text(g,yy+47,names[i],20,INK,True);b.text(g,yy+79,opts[i],14,MUTED)
        b.right(g+w,yy+46,prices[i],20,INK,True);b.button(g,yy+116,44,'−',kind='disabled',h=44);b.text(g+63,yy+125,'1',17);b.button(g+99,yy+116,44,'+',kind='secondary',h=44)
        b.button(g+154,yy+116,96,'수량 적용',kind='disabled',h=44);b.right(g+w,yy+128,'삭제',14,MUTED);b.line(g,yy+184,g+w,yy+184)
    if m:b.summary(g,636,343);b.footer(1050)
    else:b.summary(984,262,376);b.text(g,697,'계속 쇼핑하기 →',15,BRAND);b.footer(814)
    b.save('cart-mobile' if m else 'cart-desktop')

def checkout(m=False):
    b=Board(375 if m else 1440,1660 if m else 1290);g=b.g;b.header(True);b.title(104 if m else 128,'주문 확인','배송지와 주문 상품을 확인해 주세요.')
    x=g;y=232 if m else 252;w=343 if m else 824
    b.text(x,y,'01   배송지',22,INK,True);b.rect(x,y+48,w,140,SOFT,10,BRAND,2);b.radio(x+18,y+68,True)
    b.text(x+49,y+65,'집 · 기본 배송지',16,BRAND,True);b.text(x+20,y+107,'수령인 예시 · 010-****-0000',14)
    b.text(x+20,y+136,'배송 주소 예시 (실제 주소 아님)',14,MUTED);b.text(x,y+206,'배송지 관리 →',14,BRAND)
    yy=y+270;b.text(x,yy,'02   주문 상품',22,INK,True)
    for i in range(2):b.text(x,yy+53+i*42,names[i]+' · 1개',16);b.right(x+w,yy+53+i*42,prices[i],16,INK,True)
    b.line(x,yy+137,x+w,yy+137);yy+=173;b.text(x,yy,'03   쿠폰',22,INK,True)
    b.field(x,yy+47,w,'사용할 쿠폰','쿠폰 사용 안 함','⌄');b.wrap(x,yy+139,'쿠폰 할인은 주문 준비 후 서버가 확정한 금액에 반영돼요.',w,14,MUTED)
    sy=986 if m else 252;b.summary(g if m else 984,sy,343 if m else 376,True)
    if not m:b.wrap(1008,640,'이 단계는 결제 완료가 아닙니다. 준비가 끝나면 결제수단 선택 화면으로 이어집니다.',328,14,MUTED)
    else:b.wrap(16,1358,'아직 결제되지 않았어요. 준비 후 결제수단을 선택합니다.',343,14,MUTED)
    b.footer(1450 if m else 1080);b.save('checkout-mobile' if m else 'checkout-desktop')

def login(m=False):
    b=Board(375 if m else 1440,940 if m else 1000);g=b.g;b.header(True)
    if not m:
        b.rect(80,160,570,618,BRAND,16);b.orbit(188,290,290,APRICOT);b.text(120,202,'PAW / CYCLE',14,APRICOT,True)
        b.text(120,627,'함께 사는 일상을',32,'white',True);b.text(120,674,'이어가세요.',32,'white',True)
    x=16 if m else 800;y=116 if m else 191;w=343 if m else 440
    b.text(x,y,'로그인',28 if m else 36,INK,True);b.wrap(x,y+58,'로그인하면 장바구니로 돌아갑니다.',w,16,MUTED)
    b.field(x,y+123,w,'아이디');b.field(x,y+229,w,'비밀번호','','표시')
    b.button(x,y+340,w,'로그인하고 계속하기',h=52);b.text(x,y+416,'상품은 로그인 없이 둘러볼 수 있어요.',14,MUTED);b.text(x,y+456,'상품 둘러보기 →',15,BRAND,True)
    b.footer(732 if m else 830,True);b.save('login-mobile' if m else 'login-desktop')

def core(kind,m=False):
    b=Board(375 if m else 1440,1870 if m else 1400);g=b.g;b.header(True)
    titles={'order-detail':'주문 상세','subscription-new':'정기배송 시작','subscription-detail':'내 정기배송'}
    b.title(104 if m else 128,titles[kind],{'order-detail':'주문번호 SAMPLE-20260830','subscription-new':'반려동물에게 맞는 플랜을 골라 주세요.','subscription-detail':'보리 · 데일리 사료 플랜'}[kind])
    y=230 if m else 262;w=343 if m else 800;x=g;rx=960;rw=400
    if kind=='order-detail':
        b.rect(x,y,w,126,SOFT,12);b.text(x+24,y+20,'배송 완료',28,BRAND,True);b.text(x+24,y+72,'결제 완료   ·   2026.08.29 배송 완료',14,MUTED)
        yy=y+161;b.text(x,yy,'주문 상품',22,INK,True)
        b.text(x,yy+50,'데일리 사료 · 2.5kg',18,INK,True);b.text(x,yy+85,'1개 · 구매 당시 상품 정보',14,MUTED);b.right(x+w,yy+51,'32,000원',20,INK,True)
        b.line(x,yy+128,x+w,yy+128);b.button(x,yy+153,160,'다시 장바구니에',kind='secondary');b.button(x+172,yy+153,140,'반품 요청',kind='secondary')
        yy+=259;b.text(x,yy,'배송 정보',22,INK,True);b.text(x,yy+48,'수령인 예시 · 010-****-0000',15);b.text(x,yy+82,'배송 주소 예시 (실제 주소 아님)',14,MUTED)
        b.text(x,yy+139,'취소·반품·환불',22,INK,True);b.text(x,yy+184,'접수된 요청이 없습니다.',15,MUTED)
        sy=1090 if m else y;b.rect(g if m else rx,sy,w if m else rw,267,SOFT,12);sx=(g if m else rx)+24
        b.text(sx,sy+25,'결제 내역',22,INK,True)
        for j,(a,v) in enumerate([('상품 금액','32,000원'),('배송비','3,000원'),('결제 금액','35,000원')]):b.text(sx,sy+80+j*45,a,14);b.right(sx+(w if m else rw)-48,sy+80+j*45,v,18,BRAND,j==2)
        yy=1410 if m else 603;xx=g if m else rx;b.rect(xx,yy,w if m else rw,176,'#FAEEE5',12)
        b.text(xx+24,yy+22,'다음에도 필요한 상품이라면',18,INK,True);b.wrap(xx+24,yy+58,'호환 플랜을 확인하고 정기배송을 직접 선택하세요.',(w if m else rw)-48,14,MUTED);b.text(xx+24,yy+126,'정기배송 옵션 보기 →',15,BRAND,True)
    elif kind=='subscription-new':
        b.text(x,y,'01   반려동물',22,INK,True);b.rect(x,y+48,w,78,SOFT,10,BRAND,2);b.radio(x+18,y+77,True);b.text(x+50,y+73,'보리 · 강아지',18,BRAND,True);b.text(x,y+148,'반려동물 등록 →',14,BRAND)
        yy=y+212;b.text(x,yy,'02   호환 플랜',22,INK,True);b.rect(x,yy+48,w,189,'white',12,BRAND,2)
        b.radio(x+20,yy+72,True);b.text(x+54,yy+68,'데일리 사료 플랜',18,INK,True);b.text(x+24,yy+112,'32,000원 / 회차',20,BRAND,True);b.text(x+24,yy+153,'1개 상품 구성',14,MUTED);b.text(x+24,yy+200,'선택됨 · 현재 판매 중인 호환 플랜',13,BRAND)
        yy+=293;b.text(x,yy,'03   배송 주기',22,INK,True)
        for i,t in enumerate(['2주','4주 선택','8주']):b.chip(x+i*112,yy+49,102,t,i==1)
        b.wrap(x,yy+118,'다음 주문 예정일은 구독을 만든 뒤 확인할 수 있습니다.',w,16,MUTED)
        sy=1096 if m else y;xx=g if m else rx;ww=w if m else rw;b.rect(xx,sy,ww,346,SOFT,12)
        b.text(xx+24,sy+25,'선택한 정기배송',22,INK,True);b.text(xx+24,sy+78,'보리 · 데일리 사료 플랜',16);b.text(xx+24,sy+120,'4주마다 · 32,000원 / 회차',18,BRAND,True)
        b.wrap(xx+24,sy+172,'일반 주문 결제와 별개의 구독 생성입니다.',ww-48,14,MUTED);b.button(xx+24,sy+262,ww-48,'정기배송 시작하기',h=52)
    else:
        b.rect(x,y,w,213,BRAND,12);b.text(x+24,y+20,'진행 중 · 다음 주문 예정',14,APRICOT,True);b.text(x+24,y+53,'9월 10일',38,'white',True);b.text(x+24,y+112,'데일리 사료 2.5kg × 1',18,'white');b.text(x+24,y+153,'32,000원 · 적용 주기 8주',18,'white',True);b.orbit(x+w-100,y+38,65,APRICOT)
        yy=y+246;b.button(x,yy,160,'다음 날짜 변경',kind='secondary');b.button(x+172,yy,160,'회차 건너뛰기',kind='secondary')
        yy+=91;b.rect(x,yy,w,156,'#FAEEE5',12);b.text(x+22,yy+21,'다음부터 적용할 변경',19,INK,True)
        b.text(x+22,yy+62,'8주 주기 · 9월 10일 회차부터 적용',14,BRAND,True);b.wrap(x+22,yy+98,'이번 표시 날짜와 이후 주기를 구분해 확인하세요.',w-44,14,MUTED)
        yy+=205;b.text(x,yy,'이번 회차 추가 상품',22,INK,True);b.text(x,yy+47,'추가한 상품이 없습니다.',15,MUTED);b.text(x,yy+85,'추가 가능한 상품 보기 →',14,BRAND)
        yy+=157;b.text(x,yy,'배송 일정과 변경 이력',22,INK,True);b.line(x+8,yy+56,x+8,yy+139,LINE,2)
        for j,(a,v) in enumerate([('09.10  예정','변경 예정 주기 적용'),('08.30  주기 변경','변경 요청 반영 기록')]):
            b.d.ellipse((x,yy+58+j*60,x+16,yy+74+j*60),fill=BRAND);b.text(x+32,yy+51+j*60,a,16,INK,True);b.text(x+32,yy+79+j*60,v,13,MUTED)
        sy=1360 if m else y;xx=g if m else rx;ww=w if m else rw;b.text(xx,sy,'정기배송 관리',22,INK,True)
        for j,t in enumerate(['플랜·주기 변경 →','배송지 관리 →','일시정지 →','정기배송 해지 →']):
            b.text(xx,sy+55+j*48,t,16,ERROR if j==3 else BRAND);b.line(xx,sy+91+j*48,xx+ww,sy+91+j*48)
    b.footer(1660 if m else 1190);b.save(kind+('-mobile' if m else '-desktop'))

def identity():
    b=Board(1440,1050);b.wordmark(80,64);b.text(80,142,'A의 구조는 유지하고, PawCycle의 일상을 드러내기',32,INK,True)
    b.text(80,203,'A / R1 · Daily Orbit · 구조상 우선 후보, 최종 선택 아님',16,MUTED)
    for i,(c,t) in enumerate([(BRAND,'BRAND'),(APRICOT,'ACCENT'),(SOFT,'SURFACE'),(INK,'INK'),(ERROR,'ERROR'),(SALE,'SALE')]):
        x=80+i*214;b.rect(x,268,190,100,c,10);b.text(x,385,t,13,INK,True);b.text(x,410,c,14,MUTED)
    b.text(80,497,'함께 사는 일상, 필요한 만큼.',34,INK,True);b.text(80,556,'가격과 상태는 또렷하게. 브랜드는 작은 반복으로.',18,MUTED)
    b.orbit(100,649,145);b.text(299,651,'PAW / CYCLE',25,BRAND,True);b.wrap(299,702,'두 개의 열린 궤도. 반려동물과 사람, 이번과 다음의 반복. 상품 탐색보다 앞에 나서지 않습니다.',400,18,MUTED)
    for i in range(2):b.photo(780+i*288,492,268,268,i)
    b.button(80,859,186,'장바구니에 담기');b.button(284,859,160,'배송 주기 변경',kind='secondary');b.chip(462,863,112,'4주 선택',True)
    b.text(80,959,'사진은 AI 생성 가상 패키지. 실제 PB 상품/브랜드 출시 제안이나 Production 상품이 아닙니다.',14,MUTED)
    b.save('identity')

def states():
    b=Board(1440,1160);b.wordmark(80,45);b.text(80,122,'작은 상태까지 같은 언어로',32,INK,True)
    labels=['Default','Hover','Pressed','Focus','Disabled','Loading']
    for i,label in enumerate(labels):
        x=80+i*216;b.text(x,205,label,14,MUTED);b.button(x,242,190,'처리 중' if i==5 else '장바구니에',kind='disabled' if i==4 else 'primary')
        if i in (1,2):
            b.rect(x,242,190,48,HOVER if i==1 else '#301847',8);b.text(x+44,255,'장바구니에',16,'white',True)
        if i==3:b.d.rounded_rectangle((x-5,237,x+195,295),radius=11,outline=BRAND,width=3)
    b.field(80,340,374,'배송지 별칭','집');b.field(532,340,374,'받는 분 이름','');b.field(984,340,374,'정렬','추천순','⌄')
    b.d.rounded_rectangle((532,370,906,422),radius=8,outline=ERROR,width=2);b.text(532,435,'이름을 입력해 주세요.',14,ERROR)
    b.text(80,497,'Native checkbox / radio + styled label',18,INK,True)
    for i,(t,on) in enumerate([('구매 가능한 상품',False),('정기배송 대상',True)]):b.check(80+i*270,541,on);b.text(108+i*270,539,t,15)
    b.radio(685,541,True);b.text(714,539,'4주마다',15,BRAND,True);b.chip(983,530,182,'강아지 · 지우기',True)
    b.rect(80,615,600,122,'#FFF0F2',12);b.text(104,637,'입력을 확인해 주세요',20,ERROR,True);b.text(104,681,'배송지 오류는 해당 필드에서 수정할 수 있어요.',15,ERROR)
    b.rect(760,615,600,122,'#FFF2D8',12);b.text(784,637,'10% 할인 · 상태 표현 예시',20,SALE,True);b.text(784,681,'서버가 제공한 실제 할인에만 사용합니다.',15,SALE)
    b.text(80,803,'상품을 준비하고 있어요',24,INK,True);b.text(80,849,'무필터 0건은 오류가 아닙니다.',16,MUTED);b.button(80,898,184,'상품 둘러보기',kind='secondary')
    b.rect(760,795,600,194,SOFT,12);b.text(784,818,'필터 · 반려동물',20,INK,True);b.radio(784,866,True);b.text(814,862,'강아지',16);b.button(1135,919,200,'적용하기');b.text(784,930,'초기화',14,BRAND)
    b.text(80,1080,'포커스는 3px ring + 2px white gap. native select의 OS option popup 외형은 허용합니다.',15,MUTED);b.save('states')

def critical_states():
    b=Board(1440,1160);b.text(80,65,'결제·구독의 중요한 상태',34,INK,True)
    panels=[('결제수단 선택','준비 완료 · 아직 결제되지 않았어요.','서버 확정 금액 47,000원','Toss 결제 영역',BRAND,SOFT),('결제 상태 확인 중','결과를 아직 확인하지 못했어요.','주문 상세에서 상태를 확인해 주세요.','주문 상세 보기', '#805000','#FFF3D6'),('다음 회차가 보류되었어요','정기배송 진행 중 · 회차 보류','배송지를 등록해 주세요.','배송지 등록', '#805000','#FFF3D6'),('다른 변경이 먼저 반영됐어요','최신 정보를 불러온 뒤 다시 선택해 주세요.','이전 입력으로 자동 재실행하지 않습니다.','최신 정보 확인',ERROR,'#FFF0F2')]
    for i,(h,s,v,a,c,bg) in enumerate(panels):
        x=80+i%2*660;y=173+i//2*435;b.rect(x,y,620,380,bg,12);b.text(x+24,y+29,h,25,c,True);b.wrap(x+24,y+91,s,570,16,MUTED);b.wrap(x+24,y+150,v,570,20,INK,True)
        if i==0:
            b.rect(x+24,y+211,572,82,'white',8,EDGE);b.text(x+40,y+226,'실제 결제 UI는 Toss가 제공',18,BRAND,True);b.text(x+40,y+257,'브랜드 컨트롤로 재구현하지 않음',14,MUTED)
        else:b.button(x+24,y+247,572,a,kind='secondary',h=52)
        b.text(x+24,y+330,'제안 상태 시안 · 서버 결과만 권위값으로 표시',12,MUTED)
    b.text(80,1090,'결제 UNKNOWN에는 다시 결제 버튼이 없습니다. 구독 회차 보류를 구독 자체의 새 상태로 만들지 않습니다.',14,MUTED);b.save('critical-states')

if __name__=='__main__':
    for mobile in (False,True):
        for f in (home,plp,pdp,cart,checkout,login):f(mobile)
        for kind in ('order-detail','subscription-new','subscription-detail'):core(kind,mobile)
    identity()
    states();critical_states()
    print('21 R1 static review boards rendered')
