@echo off
echo 正在启动 LetChat Vue3 开发服务器...
echo.
echo 项目路径: %cd%
echo.
echo 正在检查依赖...
if not exist "node_modules" (
    echo 正在安装依赖...
    call npm install
)

echo.
echo 正在启动开发服务器...
echo 访问地址: http://localhost:5173
echo.
echo 按 Ctrl+C 停止服务器
call npm run dev
pause