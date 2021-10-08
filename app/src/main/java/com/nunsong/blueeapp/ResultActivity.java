package com.nunsong.blueeapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class ResultActivity extends Activity {

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        //받아온 값 불러내기
        Intent intent =getIntent();
        String time = intent.getStringExtra("time");
        int leftCrossCount = intent.getIntExtra("leftCrossCount", 0);
        int rightCrossCount = intent.getIntExtra("rightCrossCount", 0);
        int backLeanCount = intent.getIntExtra("backLeanCount", 0);
        int frontLeanCount = intent.getIntExtra("frontLeanCount", 0);
        int leftLeanCount = intent.getIntExtra("leftLeanCount", 0);
        int rightLeanCount = intent.getIntExtra("rightLeanCount", 0);
        int totalCount = intent.getIntExtra("totalCount",0);

        //result 에 출력
        TextView result = (TextView)findViewById(R.id.result);
        result.setText(time);

        // 결과 멘트
        TextView comment = (TextView) findViewById(R.id.resultCommentVal);
        if(totalCount == 0){
            comment.setText("아주 잘했어요!");
        }else{
            comment.setText("다음엔 더 노력해봐요!");
        }

        //다리꼬기 왼쪽
        TextView legleft = (TextView)findViewById(R.id.leg_numl);
        legleft.setText(String.valueOf(leftCrossCount + "회"));

        //다리꼬기 오른쪽
        TextView legright = (TextView)findViewById(R.id.leg_numr);
        legright.setText(String.valueOf(rightCrossCount + "회"));

        //좌기울임
        TextView left = (TextView)findViewById(R.id.lr_numl);
        left.setText(String.valueOf(leftLeanCount + "회"));

        //우기울임
        TextView right = (TextView)findViewById(R.id.lr_numr);
        right.setText(String.valueOf(rightLeanCount + "회"));

        //앞기울임
        TextView forward = (TextView)findViewById(R.id.fb_numf);
        forward.setText(String.valueOf(frontLeanCount + "회"));

        //뒤기울임
        TextView back = (TextView)findViewById(R.id.fb_numb);
        back.setText(String.valueOf(backLeanCount + "회"));

        //다리꼬기버튼 운동액티비티1로 이동
        Button exerciseBtn1 = (Button)findViewById(R.id.but_leg);
        exerciseBtn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, ExerciseLegActivity.class);
                startActivity(intent);
            }
        });

        //좌우버튼 운동액티비티2로 이동
        Button exerciseBtn2 = (Button)findViewById(R.id.but_lr);
        exerciseBtn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, ExerciseLRActivity.class);
                startActivity(intent);
            }
        });

        //앞뒤버튼 운동액티비티3로 이동
        Button exerciseBtn3 = (Button)findViewById(R.id.but_fb);
        exerciseBtn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, ExerciseFBActivity.class);
                startActivity(intent);
            }
        });

        //재시작 버튼 메인액티비티 이동
        Button restartBtn = (Button) findViewById(R.id.restart_button);
        restartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ResultActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });

    }
}
